// HomePage.tsx
import { useEffect, useState, useRef } from "react";
import { Client } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import { FaArrowLeft } from "react-icons/fa";
import "./Home.css";

interface User { username:string; followed:boolean }
interface ChatMessage { from:string; to:string; text:string; timestamp:number }
interface PostMessage { from:string; text:string; timestamp:number }

function HomePage(){
  const username = localStorage.getItem("username")!;
  const initialChatServer = localStorage.getItem("chatServer") || "http://localhost:9090";
  const authServerUrl = localStorage.getItem("authServer") || initialChatServer.replace("9090","8080");

  const [chatServerUrl,setChatServerUrl] = useState<string>(initialChatServer);
  const clientRef = useRef<Client|null>(null);

  const [users,setUsers] = useState<User[]>([]);
  const [selectedUser,setSelectedUser] = useState<string|null>(null);
  const [chatMessages,setChatMessages] = useState<{[user:string]:ChatMessage[]}>({});
  const [message,setMessage] = useState("");
  const [timeline,setTimeline] = useState<PostMessage[]>([]);
  const [post,setPost] = useState("");

  // helper to resolve chat server from auth server (in case of failover)
  const resolveChatServer = async () => {
    try {
      const r = await fetch(`${authServerUrl}/auth/resolve/${encodeURIComponent(username)}`);
      if (!r.ok) throw new Error("resolve failed");
      const data = await r.json();
      if (data.chatServerIp) {
        localStorage.setItem("chatServer", data.chatServerIp);
        setChatServerUrl(data.chatServerIp);
        return data.chatServerIp;
      }
    } catch (e) {
      console.error("resolve chat server failed", e);
    }
    return null;
  };

  // load initial data from current chat server
  const loadInitial = async (server:string) => {
    try {
      const allUsers = await (await fetch(`${authServerUrl}/auth/users`)).json();
      const followData = await (await fetch(`${server}/chat/users?currentUser=${username}`)).json();
      const followMap:Record<string,boolean>={};
      followData.forEach((u:{username:string,followed:boolean})=>followMap[u.username]=u.followed);
      const merged:User[] = allUsers.filter((u:string)=>u!==username)
        .map((u:string)=>({username:u,followed:followMap[u]||false}));
      setUsers(merged);

      const timelineData = await (await fetch(`${server}/chat/timeline?currentUser=${username}`)).json();
      setTimeline(timelineData||[]);

      const chats = await (await fetch(`${server}/chat/chats?username=${username}`)).json();
      setChatMessages(chats || {});
    } catch (e) {
      console.error("initial load failed", e);
      // try resolving new chat server and retry once
      const newServer = await resolveChatServer();
      if (newServer && newServer !== server) await loadInitial(newServer);
    }
  };

  useEffect(()=>{
    loadInitial(chatServerUrl);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  },[authServerUrl]);

  // websocket connect & auto-reconnect on failover
  useEffect(()=>{
    let mounted = true;
    const connect = async (server:string) => {
      if (!mounted) return;
      if (clientRef.current) {
        try { clientRef.current.deactivate(); } catch {}
        clientRef.current = null;
      }

      const stompClient = new Client({
        webSocketFactory:()=>new SockJS(`${server}/ws`),
        reconnectDelay:0, // we'll handle reconnect logic
        debug: str => console.log(str),
        onWebSocketClose: async () => {
          console.warn("websocket closed — resolving chat server and reconnecting");
          const resolved = await resolveChatServer();
          if (resolved && resolved !== server) connect(resolved);
        },
        onStompError: async () => {
          console.warn("stomp error — resolving chat server");
          const resolved = await resolveChatServer();
          if (resolved && resolved !== server) connect(resolved);
        }
      });

      stompClient.onConnect=()=> {
        stompClient.subscribe(`/topic/timeline-${username}`,msg=>{
          const newPost:PostMessage=JSON.parse(msg.body);
          setTimeline(prev=>[...prev,newPost]);
        });
        stompClient.subscribe(`/topic/chat-${username}`,msg=>{
          const m:ChatMessage=JSON.parse(msg.body);
          const otherUser = m.from===username?m.to:m.from;
          setChatMessages(prev=>({...prev,[otherUser]:[...(prev[otherUser]||[]),m]}));
        });
      };

      stompClient.activate();
      clientRef.current = stompClient;
    };

    connect(chatServerUrl);

    return ()=>{ mounted=false; try{clientRef.current?.deactivate();}catch{} };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  },[chatServerUrl, username]);

  const sendMessage=async()=>{
    const client = clientRef.current;
    if(!selectedUser||!client||!message.trim()) return;
    const msg:ChatMessage={from:username,to:selectedUser,text:message,timestamp:Date.now()};
    try {
      client.publish({destination:"/app/chat",body:JSON.stringify(msg)});
      setMessage("");
    } catch (e) {
      console.error("send failed, resolving chat server", e);
      const resolved = await resolveChatServer();
      if (resolved) setChatServerUrl(resolved);
    }
  };

  const sendPost=async()=>{
    const client = clientRef.current;
    if(!post.trim()||!client) return;
    const p:PostMessage={from:username,text:post,timestamp:Date.now()};
    try {
      client.publish({destination:"/app/post",body:JSON.stringify(p)});
      setPost("");
    } catch (e) {
      console.error("post failed, resolving chat server", e);
      const resolved = await resolveChatServer();
      if (resolved) setChatServerUrl(resolved);
    }
  };

  const toggleFollow=async(target:string,follow:boolean)=>{
    try {
      await fetch(`${chatServerUrl}/chat/${follow?"follow":"unfollow"}?currentUser=${username}&targetUser=${target}`,{method:"POST"});
      setUsers(prev=>prev.map(u=>u.username===target?{...u,followed:follow}:u));
    } catch (e) {
      console.error("follow failed", e);
      const resolved = await resolveChatServer();
      if (resolved) setChatServerUrl(resolved);
    }
  };

  const logout=()=>{
    localStorage.clear(); window.location.href="/";
  };

  return(
    <div className="home-container">
      <div className="sidebar">
        <h3>{username}</h3>
        <button onClick={logout}>Logout</button>
        <h4>Users</h4>
        <ul>{users.map(u=>(
          <li key={u.username}>
            <span onClick={()=>setSelectedUser(u.username)}>{u.username}</span>
            {u.followed?
              <button onClick={()=>toggleFollow(u.username,false)}>Unfollow</button>:
              <button onClick={()=>toggleFollow(u.username,true)}>Follow</button>}
          </li>
        ))}</ul>
      </div>

      <div className="timeline">
        {!selectedUser?
          <div className="timeline-container">
            <h2>Timeline</h2>
            <div className="timeline-content">{timeline.map((t,idx)=>(
              <div key={idx} className="timeline-post">
                <p><b>{t.from}</b></p>
                <p>{t.text}</p>
                <p>{new Date(t.timestamp).toLocaleString()}</p>
              </div>
            ))}</div>
            <div className="timeline-input">
              <input type="text" value={post} onChange={e=>setPost(e.target.value)} placeholder="Post to timeline"/>
              <button onClick={sendPost}>Post</button>
            </div>
          </div>:
          <div className="chat-container">
            <div className="chat-header">
              <button
                className="back-button"
                onClick={() => setSelectedUser(null)}
              >
                <FaArrowLeft style={{ marginRight: "8px" }} />
                Back to Timeline
              </button>
              <h2>Chat with {selectedUser}</h2>
            </div>
            <div className="chat-content">
              {(chatMessages[selectedUser]||[]).map((m,idx)=>(
                <p key={idx}><b>{m.from}:</b> {m.text} <span>{new Date(m.timestamp).toLocaleTimeString()}</span></p>
              ))}
            </div>
            <div className="chat-input">
              <input value={message} onChange={e=>setMessage(e.target.value)}/>
              <button onClick={sendMessage}>Send</button>
            </div>
          </div>
        }
      </div>
    </div>
  );
}

export default HomePage;
