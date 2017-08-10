package com.uptop.websocket;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.uptop.tool.RequestTool;
import org.springframework.util.StringUtils;

import javax.persistence.criteria.CriteriaBuilder;
import javax.servlet.http.HttpSession;
import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * @ServerEndpoint 注解是一个类层次的注解，它的功能主要是将目前的类定义成一个websocket服务器端,
 * 注解的值将被用于监听用户连接的终端访问URL地址,客户端可以通过这个URL来连接到WebSocket服务器端
 * @author uptop
 */
@ServerEndpoint(value = "/websocketTwo/{param}",configurator=GetHttpSessionConfigurator.class)
public class WebSocketTwo {
    //静态变量，用来记录当前在线连接数。应该把它设计成线程安全的。
    private static int onlineCount = 0;

    //concurrent包的线程安全Set，用来存放每个客户端对应的MyWebSocket对象。若要实现服务端与单一客户端通信的话，可以使用Map来存放，其中Key可以为用户标识
    public static CopyOnWriteArraySet<WebSocketTwo> webSocketSet = new CopyOnWriteArraySet<WebSocketTwo>();

    //与某个客户端的连接会话，需要通过它来给客户端发送数据
    private Session session;

    //HttpSession可以用来保存信息 1.room 2.oil
    private HttpSession httpSession;

    //线程安全的map来存放房间和对应的人数
    public static ConcurrentHashMap<String,CopyOnWriteArraySet<WebSocketTwo>> concurrentHashMap = new ConcurrentHashMap();

    //当前房间已抢到油数
    public static ConcurrentHashMap<String,AtomicInteger> oilHashMap = new ConcurrentHashMap();

    //设置当前房间的油数
    public static ConcurrentHashMap<String,Integer> oilControlMap = new ConcurrentHashMap<String, Integer>();


    public static ConcurrentHashMap<String,ConcurrentHashMap<String,Integer>> userOilMap = new ConcurrentHashMap<String, ConcurrentHashMap<String, Integer>>();




    /**
     * 连接建立成功调用的方法
     *
     * @param session 可选的参数。session为与某个客户端的连接会话，需要通过它来给客户端发送数据
     */
    @OnOpen
    public void onOpen(@PathParam(value="param") String param,Session session, EndpointConfig config) {
        //设置总webSocket属性
        webSocketSet.add(this);     //加入set中
        addOnlineCount();           //在线数加1
        System.out.println("有新连接加入！当前总在线人数为" + getOnlineCount());
        JSONObject jsonObject = ParamToRoomAndOpenId(param);
        if(jsonObject!=null){
            String room = (String)jsonObject.get("room");
            String openId = (String)jsonObject.get("openId");
            //设置用户session相关属性
            this.session = session;
            this.httpSession = (HttpSession) config.getUserProperties()
                    .get(HttpSession.class.getName());
            this.httpSession.setAttribute("room",room);
            this.httpSession.setAttribute("openId",openId);
            this.httpSession.setAttribute("oilNum",0);
            System.out.println("房间号为"+room+",openId为"+openId+"进入房间!");

            //init room
            if(concurrentHashMap.get(room)==null){
                concurrentHashMap.put(room,new CopyOnWriteArraySet<WebSocketTwo>());
            }
            //add user to room
            concurrentHashMap.get(room).add(this);

            //init oil num
            if(oilHashMap.get(room)==null){
                oilHashMap.put(room,new AtomicInteger(0));
            }

            if(oilControlMap.get(room)==null){
                oilControlMap.put(room,500);
            }

            if(userOilMap.get(room)==null){
                userOilMap.put(room,new ConcurrentHashMap<String, Integer>());
            }
            if(userOilMap.get(room).get(openId)==null){
                userOilMap.get(room).put(openId,0);
            }else{
                int userOilNum = userOilMap.get(room).get(openId);
                this.httpSession.setAttribute("oilNum",userOilNum);
            }
            JSONArray jsonArray = new JSONArray();
            for (WebSocketTwo item : concurrentHashMap.get(this.httpSession.getAttribute("room"))) {
                JSONObject jsonObject1 = new JSONObject();
                jsonObject1.put("openId",item.httpSession.getAttribute("openId"));
                jsonObject1.put("oilNum",item.httpSession.getAttribute("oilNum"));
                jsonArray.add(jsonObject1);
            }
            JSONObject returnObj = new JSONObject();
            returnObj.put("type",0);
            returnObj.put("totalOilNum",oilControlMap.get(room)-oilHashMap.get(room).get());
            returnObj.put("jsonArray",jsonArray);
            String returnStr = JSON.toJSONString(returnObj);

            for (WebSocketTwo item : concurrentHashMap.get(this.httpSession.getAttribute("room"))) {
                try {
                    synchronized (item){
                        //item.session.getAsyncRemote().sendText(returnStr);
                        item.session.getBasicRemote().sendText(returnStr);
                        System.out.println("=================="+item.session.getMaxTextMessageBufferSize());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    webSocketSet.remove(this);  //从set中删除
                    subOnlineCount();           //在线数减1
                    concurrentHashMap.get(this.httpSession.getAttribute("room")).remove(this);
                    System.out.println("有一连接关闭！当前在线人数为" + getOnlineCount());
                    try {
                        item.session.close();
                    } catch (IOException e1) {
                        // Ignore
                    }
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
        concurrentHashMap.get(this.httpSession.getAttribute("room")).remove(this);
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
        this.sendMessage(message);
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
    public void sendMessage(String message) {
        System.out.println("来自客户端的消息:" + message);
        System.out.println("当前房间号:"+this.httpSession.getAttribute("room"));
        if(oilHashMap.get(this.httpSession.getAttribute("room")).get()<oilControlMap.get(this.httpSession.getAttribute("room"))){
            oilHashMap.get(this.httpSession.getAttribute("room")).getAndIncrement();
            this.httpSession.setAttribute("oilNum",Integer.parseInt(String.valueOf(this.httpSession.getAttribute("oilNum")))+1);
            userOilMap.get(this.httpSession.getAttribute("room")).put(String.valueOf(this.httpSession.getAttribute("openId")),Integer.parseInt(String.valueOf(this.httpSession.getAttribute("oilNum"))));
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("openId",this.httpSession.getAttribute("openId"));
            jsonObject.put("oilNum",this.httpSession.getAttribute("oilNum"));
            jsonObject.put("totalOilNum",oilControlMap.get(this.httpSession.getAttribute("room"))-oilHashMap.get(this.httpSession.getAttribute("room")).get());
            jsonObject.put("type",1);
            String returnStr = JSON.toJSONString(jsonObject);

            //群发消息
            while(true) {
                for (WebSocketTwo item : concurrentHashMap.get(this.httpSession.getAttribute("room"))) {
                    try {
                        synchronized (item) {
                            if(!item.session.isOpen()){
                                System.out.println("哈哈不行啦");
                            }
                            //item.session.getBasicRemote().setBatchingAllowed(true);
                            //item.session.getBasicRemote().flushBatch();
                            item.session.getBasicRemote().sendText(returnStr);
                            //item.session.getAsyncRemote().sendText(returnStr);

                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        webSocketSet.remove(this);  //从set中删除
                        subOnlineCount();           //在线数减1
                        concurrentHashMap.get(this.httpSession.getAttribute("room")).remove(this);
                        System.out.println("有一连接关闭！当前在线人数为" + getOnlineCount());
                        try {
                            item.session.close();
                        } catch (IOException e1) {
                            // Ignore
                        }
                        continue;
                    }
                }
            }
        }
    }

    public static synchronized int getOnlineCount() {
        return onlineCount;
    }

    public static synchronized void addOnlineCount() {
        WebSocketTwo.onlineCount++;
    }

    public static synchronized void subOnlineCount() {
        WebSocketTwo.onlineCount--;
    }

    //检查房间是否有效
    public JSONObject ParamToRoomAndOpenId(String stringParam){
        Map<String, String> requestParamMap = RequestTool.RequestParam(stringParam);
        String room = requestParamMap.get("room");
        String openId = requestParamMap.get("openId");

        if(StringUtils.isEmpty(room)||StringUtils.isEmpty(openId)){
            return null;
        }else{
            if(!StringUtils.isEmpty(requestParamMap.get("totalOilNum"))){
                int totalOilNum = Integer.parseInt(requestParamMap.get("totalOilNum"));
                oilControlMap.put(room,totalOilNum);
                oilHashMap.put(room,new AtomicInteger(0));
            }
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("room",room);
            jsonObject.put("openId",openId);
            return jsonObject;
        }
    }

    //推送给所有人信息
    public void sendAllMessage(String message) throws IOException {
        this.session.getBasicRemote().sendText(message);
    }

    public void sendAllMsg(String msg) {
        for (WebSocketTwo item : webSocketSet) {
            try {
                item.sendAllMessage(msg);
            } catch (IOException e) {
                e.printStackTrace();
                continue;
            }
        }
    }
}
