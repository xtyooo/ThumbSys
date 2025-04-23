package com.xty.thumbsys.listener.thumb;//package com.xty.thumbsys.listener.thumb;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xty.thumbsys.listener.thumb.msg.ThumbEvent;
import com.xty.thumbsys.mapper.BlogMapper;
import com.xty.thumbsys.model.entity.Thumb;
import com.xty.thumbsys.service.ThumbService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.client.api.SubscriptionType;
import org.apache.pulsar.common.schema.SchemaType;
import org.apache.pulsar.shade.org.apache.commons.lang3.tuple.Pair;
import org.springframework.pulsar.annotation.PulsarListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;


/**
 * 点赞消息消费者，处理异步点赞事件
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ThumbConsumer {

    private final BlogMapper blogMapper;
    private final ThumbService thumbService;


    /**
     * 处理死信队列消息
     * @param message
     */
    @PulsarListener(topics = "thumb-dlq-topic")
    public void consumerDlq(Message<ThumbEvent> message) {
        MessageId messageId = message.getMessageId();
        log.info("dlq message = {}", messageId);
        log.info("消息 {} 已入库", messageId);
        log.info("已通知相关人员 {} 处理消息 {}", "xtyooo", messageId);
    }





    // 批量处理配置
    @PulsarListener(
            subscriptionName = "thumb-subscription",
            topics = "thumb-topic",
            schemaType = SchemaType.JSON,
            batch = true,
            subscriptionType = SubscriptionType.Shared,
            // nack重试策略
            negativeAckRedeliveryBackoff = "negativeAckRedeliveryBackoff",
//            // ack超时重试策略
//            ackTimeoutRedeliveryBackoff = "ackTimeoutRedeliveryBackoff",
            // 死信队列策略
            deadLetterPolicy = "deadLetterPolicy"
    )
    @Transactional(rollbackFor = Exception.class)
    public void processBatch(List<Message<ThumbEvent>> messages) {
        log.info("ThumbConsumer processBatch: {}", messages.size());
        for (Message<ThumbEvent> message : messages) {
            log.info("message.getMessageId() = {}", message.getMessageId());
        }
        //测试死信队列
        if (true) {
            throw new RuntimeException("ThumbConsumer processBatch failed");
        }
        Map<Long, Long> countMap = new ConcurrentHashMap<>();
        List<Thumb> thumbs = new ArrayList<>();

        // 并行处理消息
        LambdaQueryWrapper<Thumb> wrapper = new LambdaQueryWrapper<>();
        AtomicReference<Boolean> needRemove = new AtomicReference<>(false);

        // 提取事件并过滤无效消息
        List<ThumbEvent> events = messages.stream()
                .map(Message::getValue)
                .filter(Objects::nonNull)
                .toList();


        // 按(userId, blogId)分组，并获取每个分组的最新事件
        Map<Pair<Long, Long>, ThumbEvent> latestEvents = events.stream()
                .collect(Collectors.groupingBy(
                        e -> Pair.of(e.getUserId(), e.getBlogId()),
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                list -> {
                                    // 按时间升序排序，取最后一个作为最新事件
                                    list.sort(Comparator.comparing(ThumbEvent::getEventTime));
                                    if (list.size() % 2 == 0) {
                                        return null;
                                    }
                                    return list.get(list.size() - 1);
                                }
                        )
                ));

        latestEvents.forEach((userBlogPair, event) -> {
            if (event == null) {
                return;
            }
            ThumbEvent.EventType finalAction = event.getType();

            if (finalAction == ThumbEvent.EventType.INCR) {
                countMap.merge(event.getBlogId(), 1L, Long::sum);
                Thumb thumb = new Thumb();
                thumb.setBlogId(event.getBlogId());
                thumb.setUserId(event.getUserId());
                thumbs.add(thumb);
            } else {
                needRemove.set(true);
                wrapper.or().eq(Thumb::getUserId, event.getUserId()).eq(Thumb::getBlogId, event.getBlogId());
                countMap.merge(event.getBlogId(), -1L, Long::sum);
            }
        });

        // 批量更新数据库
        if (needRemove.get()) {
            thumbService.remove(wrapper);
        }
        batchUpdateBlogs(countMap);
        batchInsertThumbs(thumbs);
    }

    public void batchUpdateBlogs(Map<Long, Long> countMap) {

            if (!countMap.isEmpty()) {
                try {
                    log.info("开始批量更新博客点赞数，参数: {}", countMap);
                    blogMapper.batchUpdateThumbCount(countMap);
                    log.info("批量更新博客点赞数成功");
                } catch (Exception e) {
                    log.error("批量更新博客点赞数时出错，参数: {}", countMap, e);
                    throw e;
                }
            }

    }

    public void batchInsertThumbs(List<Thumb> thumbs) {
        if (!thumbs.isEmpty()) {
            // 分批次插入
            thumbService.saveBatch(thumbs, 500);
        }
    }
}
