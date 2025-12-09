import { useParams, useNavigate } from "react-router-dom";
import { useState } from "react";

export default function ChatPage() {
  const { userId } = useParams();
  const [msg, setMsg] = useState("");
  const navigate = useNavigate();

  const sendMessage = () => {
    console.log("Send to:", userId, msg);
    setMsg("");
  };

  return (
    <div className="flex flex-col h-screen">
      <div className="p-2 bg-gray-200 flex justify-between">
        <button onClick={() => navigate("/home")}>&larr; Back</button>
        <p className="font-bold">Chat with {userId}</p>
      </div>
      <div className="flex-1 p-4 overflow-y-auto">
        {/* TODO: load chat history */}
        <p>No messages yet...</p>
      </div>
      <div className="p-2 border-t flex">
        <input className="flex-1 border p-2"
          value={msg} onChange={e => setMsg(e.target.value)} />
        <button className="bg-blue-500 text-white px-4" onClick={sendMessage}>Send</button>
      </div>
    </div>
  );
}
