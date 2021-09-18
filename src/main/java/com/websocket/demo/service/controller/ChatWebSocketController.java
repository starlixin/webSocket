package com.websocket.demo.service.controller;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.websocket.demo.service.enity.MyMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @Author: Xin.Li
 * @Date: 2021/9/16
 * @Descript:聊天控制器
 * @ServerEndpoint("/chat/{userId}")中的userId是前端创建会话窗口时当前用户的id,即消息发送者的id
 */
@ServerEndpoint("/chat/{userId}")
@Component
@Slf4j
public class ChatWebSocketController {
    /**
     * 在线人数
     */
    public  static AtomicInteger onlineCount=new AtomicInteger(0);

    /**
     * 用于存放每个客户端对应的websocket对象
     */
    public  static List<ChatWebSocketController> webSocketSet=new ArrayList<>();

    /**
     * 存放所以连接人信息
     */
    public  static List<String> userList=new ArrayList<>();
    /**
     * 与某个客户端的连接会话，需要通过它来给客户端发送数据
     */
    private Session session;

    /**
     * 用户ID
     */
    public String userId="";

    /**
     * 连接建立成功调用的方法
     * @param session
     * @param userId
     */
    @OnOpen
    public void onOpen(Session session, @PathParam("userId") String userId){

        try {this.session = session;
        this.userId = userId;
        userList.add(userId) ;
        //加入set中
        webSocketSet.add(this);
        //在线数加1
        onlineCount.incrementAndGet();
        log.info("有新连接加入！" + userId + "当前在线用户数为" + onlineCount.get());
        JSONObject msg = new JSONObject();

            msg.put("msg", "连接成功");
            msg.put("status", "SUCCESS");
            msg.put("userId", userId);
            sendMessage(JSONUtil.toJsonStr(msg));
        } catch (Exception e) {
            log.error("IO异常",e);
        }

    }

    @OnClose
    public void onClose(@PathParam("userId") String userId){
        //从set中删除
        webSocketSet.remove(this);
        onlineCount.decrementAndGet(); // 在线数减1
        log.info("用户"+ userId +"退出聊天！当前在线用户数为" + onlineCount.get());

    }

    @OnMessage
    public void onMessage(String message,@PathParam("userId")String userId){
        //客户端输入的消息message要经过处理后封装成新的message,后端拿到新的消息后进行数据解析,然后判断是群发还是单发,并调用对应的方法
        log.info("来自客户端" + userId + "的消息:" + message);
        try {
            MyMessage myMessage = JSONUtil.toBean(message, MyMessage.class);
            log.info("消息:{}",JSONUtil.toJsonStr(myMessage));
            String messageContent = myMessage.getMessage();//messageContent：真正的消息内容
            String messageType = myMessage.getMessageType();
            if("1".equals(messageType)){ //单聊
                String recUser = myMessage.getUserId();//recUser：消息接收者
                sendInfo(messageContent,recUser,userId);//messageContent：输入框实际内容 recUser：消息接收者  userId 消息发送者
            }else{ //群聊
                sendGroupInfo(messageContent,userId);//messageContent：输入框实际内容 userId 消息发送者
            }
        } catch (Exception e) {
            log.error("解析失败：{}", e);
        }

    }

    @OnError
    public void onError(Throwable e){
        log.error("websocket 异常",e);
    }

    /**
     * @param message
     */
    public synchronized void sendMessage(String message){
        Future<Void> voidFuture = this.session.getAsyncRemote().sendText(message+"后端消息");
        log.info("发送成功：{}"+JSONUtil.toJsonStr(voidFuture));
    }

    /**
     * 单聊
     * message ： 消息内容，输入的实际内容，不是拼接后的内容
     * recUser : 消息接收者
     * sendUser : 消息发送者
     */
    public void sendInfo( String message , String recUser,String sendUser) {
        JSONObject msgObject = new JSONObject();//msgObject 包含发送者信息的消息
        for (ChatWebSocketController item : webSocketSet) {
            if (StrUtil.equals(item.userId, recUser)) {
                log.info("给用户{}传递消息:{}",recUser  , message);
                //拼接返回的消息，除了输入的实际内容，还要包含发送者信息
                msgObject.put("message",message);
                msgObject.put("sendUser",sendUser);
                item.sendMessage(JSONUtil.toJsonStr(msgObject));
            }
        }
    }

    /**
     * 群聊
     * message ： 消息内容，输入的实际内容，不是拼接后的内容
     * sendUser : 消息发送者
     */
    public  void sendGroupInfo(String message,String sendUser) {
        JSONObject msgObject = new JSONObject();//msgObject 包含发送者信息的消息
        if (CollUtil.isNotEmpty(webSocketSet)) {
            for (ChatWebSocketController item : webSocketSet) {
                if(!StrUtil.equals(item.userId, sendUser)) { //排除给发送者自身回送消息,如果不是自己就回送
                    log.info("回送消息:" + message);
                    //拼接返回的消息，除了输入的实际内容，还要包含发送者信息
                    msgObject.put("message",message);
                    msgObject.put("sendUser",sendUser);
                    item.sendMessage(JSONUtil.toJsonStr(msgObject));
                }
            }
        }
    }

    /**
     * Map/Set的key为自定义对象时，必须重写hashCode和equals。
     * 关于hashCode和equals的处理，遵循如下规则：
     * 1）只要重写equals，就必须重写hashCode。
     * 2）因为Set存储的是不重复的对象，依据hashCode和equals进行判断，所以Set存储的对象必须重写这两个方法。
     * 3）如果自定义对象做为Map的键，那么必须重写hashCode和equals。
     *
     * @param o
     * @return
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ChatWebSocketController that = (ChatWebSocketController) o;
        return Objects.equals(session, that.session);
    }

    @Override
    public int hashCode() {
        return Objects.hash(session);
    }
}
