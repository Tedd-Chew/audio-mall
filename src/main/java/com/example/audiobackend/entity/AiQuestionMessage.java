package com.example.audiobackend.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor  // 【必备】Spring反序列化必须有无参构造
@AllArgsConstructor // 【方便】一键创建对象
public class AiQuestionMessage implements Serializable {
    private String visitorId;   // 核心：用户唯一标识（Cookie获取）
    private String question;    // 用户问题
}//这个序列化接口告诉ai，这个对象可以被序列化，即转换为字节流，之后也可以被反序列化
//因为要先放到队列中，所以要实现序列化接口，之后还要传给消费者处理，所以之后要反序列化

