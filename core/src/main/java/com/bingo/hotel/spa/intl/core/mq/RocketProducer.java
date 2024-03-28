package com.bingo.hotel.spa.intl.core.mq;

import com.bingo.hotel.rocketmq.domain.BaseMessage;
import com.bingo.hotel.rocketmq.templete.RocketMQEnhanceTemplate;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;

@Component
@RequestMapping("enhance")
@Slf4j
public class RocketProducer<T extends BaseMessage> {
    //注入增强后的模板，可以自动实现环境隔离，日志记录
    @Setter(onMethod_ = @Autowired)
    private RocketMQEnhanceTemplate rocketMQEnhanceTemplate;

    public SendResult sendMessage(String topic, String tag, T message) {
        return rocketMQEnhanceTemplate.send(topic, tag, message);
    }


    public SendResult send(String destination, T message) {
        return rocketMQEnhanceTemplate.send(destination, message);
    }

    public SendResult send(String topic, String tag, T message, int delayLevel) {
        return rocketMQEnhanceTemplate.send(topic, tag, message, delayLevel);
    }

    public SendResult send(String destination, T message, int delayLevel) {
        return rocketMQEnhanceTemplate.send(destination, message, delayLevel);
    }

}
