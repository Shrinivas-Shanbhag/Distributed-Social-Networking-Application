// AuthPage.tsx
import { useState } from "react";
import { useNavigate } from "react-router-dom";
import axios from "axios";
import "./Auth.css";

const AUTH_BASE = "http://localhost:8080"; // change via env if needed

function AuthPage() {
  const [isLogin, setIsLogin] = useState(true);
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const navigate = useNavigate();

  const handleLogin = async () => {
    try {
      const res = await axios.post(`${AUTH_BASE}/auth/login`, { username, password });

      if (!res?.data) {
        alert("Login failed: invalid response");
        return;
      }

      if (res.data.status !== "success") {
        alert(res.data.message || "Login failed");
        return;
      }

      // server returned the assigned chat server master address (active)
      const chatServer = res.data.chatServerIp;
      localStorage.setItem("username", username);
      localStorage.setItem("chatServer", chatServer);
      localStorage.setItem("authServer", AUTH_BASE);
      navigate("/home");
    } catch (err) {
      console.error(err);
      alert("Server error, login failed");
    }
  };

  const handleRegister = async () => {
    if (password !== confirmPassword) {
      alert("Passwords do not match");
      return;
    }
    try {
      await axios.post(`${AUTH_BASE}/auth/register`, { username, password });
      alert("Registration successful, please login.");
      setIsLogin(true);
    } catch (err) {
      console.error(err);
      alert("Registration failed");
    }
  };

  return (
    <div className="auth-page">
      <div className="branding">
        <h1 className="app-title">LinkStream</h1>
        <p className="app-tagline">Connect, share, and chat seamlessly.</p>
      </div>

      <div className="auth-box">
        <h2 className="login-title">{isLogin ? "Login" : "Register"}</h2>

        <input
          type="text"
          placeholder="Username"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          className="auth-input"
        />
        <input
          type="password"
          placeholder="Password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          className="auth-input"
        />

        {!isLogin && (
          <input
            type="password"
            placeholder="Confirm Password"
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
            className="auth-input"
          />
        )}

        <button
          onClick={isLogin ? handleLogin : handleRegister}
          className="auth-button"
        >
          {isLogin ? "Login" : "Register"}
        </button>

        <p className="auth-footer">
          {isLogin ? (
            <>
              Don’t have an account?{" "}
              <span
                onClick={() => setIsLogin(false)}
                style={{ color: "#007bff", cursor: "pointer", fontWeight: "500" }}
              >
                Register
              </span>
            </>
          ) : (
            <>
              Already have an account?{" "}
              <span
                onClick={() => setIsLogin(true)}
                style={{ color: "#007bff", cursor: "pointer", fontWeight: "500" }}
              >
                Login
              </span>
            </>
          )}
        </p>
      </div>
    </div>
  );
}

export default AuthPage;
