package com.uptop.websocket;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * @ServerEndpoint 注解是一个类层次的注解，它的功能主要是将目前的类定义成一个websocket服务器端,
 * 注解的值将被用于监听用户连接的终端访问URL地址,客户端可以通过这个URL来连接到WebSocket服务器端
 * @author uptop
 */
@ServerEndpoint("/websocketOne/{param}")
public class WebSocketOne {
    //静态变量，用来记录当前在线连接数。应该把它设计成线程安全的。
    private static int onlineCount = 0;

    //concurrent包的线程安全Set，用来存放每个客户端对应的MyWebSocket对象。若要实现服务端与单一客户端通信的话，可以使用Map来存放，其中Key可以为用户标识
    public static CopyOnWriteArraySet<WebSocketOne> webSocketSet = new CopyOnWriteArraySet<WebSocketOne>();

    //与某个客户端的连接会话，需要通过它来给客户端发送数据
    private Session session;

    //当前所属房间号
    private int room;
    //允许建立的房间号
    public int[] rooms = new int[]{1,2,3,4,5};


    //线程安全的map来存放房间和对应的人数
    public static ConcurrentHashMap<Integer,CopyOnWriteArraySet<WebSocketOne>> concurrentHashMap = new ConcurrentHashMap();

    //当前房间油数
    public static ConcurrentHashMap<Integer,AtomicInteger> oilHashMap = new ConcurrentHashMap();

    //当前房间每个人对应油数
    public static Map<Integer,Map<String,Integer>> userOilMap = new HashMap<Integer, Map<String, Integer>>();


    /**
     * 连接建立成功调用的方法
     *
     * @param session 可选的参数。session为与某个客户端的连接会话，需要通过它来给客户端发送数据
     */
    @OnOpen
    public void onOpen(@PathParam(value="param") String param, Session session) {
        //打印房间号
        System.out.println("房间号为"+param);
        //判断房间号是否存在如果存在就返回
        int roomNum = this.ParamToRoom(param);
        if(roomNum != 0){
            //all session control
            this.session = session;
            webSocketSet.add(this);     //加入set中
            addOnlineCount();           //在线数加1

            //init room
            if(concurrentHashMap.get(roomNum)==null){
                concurrentHashMap.put(roomNum,new CopyOnWriteArraySet<WebSocketOne>());
            }
            //add user to room
            concurrentHashMap.get(roomNum).add(this);
            //remember which room belong to user
            this.room = roomNum;

            //init oil num
            if(oilHashMap.get(roomNum)==null){
                oilHashMap.put(roomNum,new AtomicInteger(0));
            }
            String name = this.getRandomString(5);
            if(userOilMap.get(roomNum)==null){
                userOilMap.put(roomNum,new HashMap<String,Integer>());
            }
            if(userOilMap.get(roomNum).get(name)==null){
                userOilMap.get(roomNum).put(name,0);
            }
            System.out.println("有新连接加入！当前在线人数为" + getOnlineCount());

            //群发消息 tell everyone i am in
            for (WebSocketOne item : concurrentHashMap.get(this.room)) {
                try {
                    JSONArray jsonArray = new JSONArray();
                    for (String key : userOilMap.get(roomNum).keySet()) {
                        JSONObject jsonObject = new JSONObject();
                        jsonObject.put("name",key);
                        if(name.equals(key)){
                            jsonObject.put("isNew",true);
                        }else{
                            jsonObject.put("isNew",false);
                        }
                        jsonArray.add(jsonObject);
                    }
                    JSONObject returnJsonObj = new JSONObject();
                    returnJsonObj.put("type",0);
                    returnJsonObj.put("names",jsonArray);
                    item.sendMessage(JSON.toJSONString(returnJsonObj));
                } catch (IOException e) {
                    e.printStackTrace();
                    continue;
                }
            }
        }
    }

    /**
     * 连接关闭调用的方法
     */
    @OnClose
    public void onClose() {
        webSocketSet.remove(this);  //从set中删除
        subOnlineCount();           //在线数减1
        concurrentHashMap.get(this.room).remove(this);
        System.out.println("有一连接关闭！当前在线人数为" + getOnlineCount());
    }

    /**
     * 收到客户端消息后调用的方法
     *
     * @param message 客户端发送过来的消息
     * @param session 可选的参数
     */
    @OnMessage
    public void onMessage(String message, Session session) {
        System.out.println("来自客户端的消息:" + message);
        System.out.println("当前房间号:"+this.room);
        if(oilHashMap.get(this.room).get()<500){
            oilHashMap.get(this.room).getAndIncrement();
            userOilMap.get(this.room).put(message,userOilMap.get(this.room).get(message)+1);
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("type",1);
            jsonObject.put("name",message);
            jsonObject.put("num",userOilMap.get(this.room).get(message));
            //群发消息
            for (WebSocketOne item : concurrentHashMap.get(this.room)) {
                try {
                    item.sendMessage(JSON.toJSONString(jsonObject));
                } catch (IOException e) {
                    e.printStackTrace();
                    continue;
                }
            }
        }
        System.out.println("total----"+oilHashMap.get(this.room).get());
    }

    /**
     * 发生错误时调用
     *
     * @param session
     * @param error
     */
    @OnError
    public void onError(Session session, Throwable error) {
        System.out.println("发生错误");
        error.printStackTrace();
    }

    /**
     * 这个方法与上面几个方法不一样。没有用注解，是根据自己需要添加的方法。
     *
     * @param message
     * @throws IOException
     */
    public void sendMessage(String message) throws IOException {
        synchronized (this.session){
            this.session.getBasicRemote().sendText(message);
        }
    }

    public static synchronized int getOnlineCount() {
        return onlineCount;
    }

    public static synchronized void addOnlineCount() {
        WebSocketOne.onlineCount++;
    }

    public static synchronized void subOnlineCount() {
        WebSocketOne.onlineCount--;
    }

    public void sendMsg(String msg,int room) {
        for (WebSocketOne item : concurrentHashMap.get(room)) {
            try {
                item.sendMessage(msg);
            } catch (IOException e) {
                e.printStackTrace();
                continue;
            }
        }
    }

    //检查房间是否有效
    public int ParamToRoom(String stringParam){
        int param = Integer.parseInt(stringParam);
        for(int r:rooms){
            if(param == r){
                return r;
            }
        }
        return 0;
    }

    private String getRandomString(int length) { //length表示生成字符串的长度
        String base = "abcdefghijklmnopqrstuvwxyz0123456789";
        Random random = new Random();
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < length; i++) {
            int number = random.nextInt(base.length());
            sb.append(base.charAt(number));
        }
        return sb.toString();
    }
}
