package com.xty.thumbsys.manager.cache;

import com.xty.thumbsys.common.AddResult;

import java.util.List;
import java.util.concurrent.BlockingQueue;

public interface TopK {

    AddResult add(String key, int increment);

    //返回当前topk元素的集合
    List<Item> list();
    //获取被淘汰出topk的元素的队列
    BlockingQueue<Item> expelled();
    //对所有计数进行衰减
    void fading();

    long total();

}
