package auth;

import org.springframework.web.bind.annotation.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Controller;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final String BASE = Paths.get("").toAbsolutePath().toString();
    private static final String USER_FILE = BASE + "/users.json";
    private static final String SERVERS_FILE = BASE + "/servers.json"; // registry of server pairs
    private static final String ASSIGN_FILE = BASE + "/userAssignments.json";

    private final ObjectMapper mapper = new ObjectMapper();
    private Map<String, String> users = new HashMap<>(); // username -> password
    private Map<String, ServerPair> serverPairs = new HashMap<>(); // pairId -> ServerPair
    private Map<String, String> userAssignments = new HashMap<>(); // username -> pairId
    private AtomicInteger rr = new AtomicInteger(0);

    private final RestTemplate rest = new RestTemplate();

    public AuthController() {
        loadUsers();
        loadServers();
        loadAssignments();
        // If servers.json missing, create a default single pair for dev
        if (serverPairs.isEmpty()) {
            ServerPair p = new ServerPair("pair1", "http://localhost:9090", "http://localhost:9091");
            p.setActiveMaster(p.master);
            serverPairs.put(p.pairId, p);
            saveServers();
        }
    }

    // ---- Persistence helpers ----
    private synchronized void loadUsers() {
        try { File f = new File(USER_FILE); if (f.exists()) users = mapper.readValue(f, new TypeReference<>(){}); }
        catch(IOException e){ e.printStackTrace(); users = new HashMap<>(); }
    }
    private synchronized void saveUsers() {
        try { mapper.writerWithDefaultPrettyPrinter().writeValue(new File(USER_FILE), users); }
        catch(IOException e){ e.printStackTrace(); }
    }

    private synchronized void loadServers() {
        try { File f = new File(SERVERS_FILE); if (f.exists()) serverPairs = mapper.readValue(f, new TypeReference<>(){}); }
        catch(IOException e){ e.printStackTrace(); serverPairs = new HashMap<>(); }
    }
    private synchronized void saveServers() {
        try { mapper.writerWithDefaultPrettyPrinter().writeValue(new File(SERVERS_FILE), serverPairs); }
        catch(IOException e){ e.printStackTrace(); }
    }

    private synchronized void loadAssignments() {
        try { File f = new File(ASSIGN_FILE); if (f.exists()) userAssignments = mapper.readValue(f, new TypeReference<>(){}); }
        catch(IOException e){ e.printStackTrace(); userAssignments = new HashMap<>(); }
    }
    private synchronized void saveAssignments() {
        try { mapper.writerWithDefaultPrettyPrinter().writeValue(new File(ASSIGN_FILE), userAssignments); }
        catch(IOException e){ e.printStackTrace(); }
    }

    // ---- Registration & Login ----
    @PostMapping("/register")
    public synchronized Map<String, String> register(@RequestBody Map<String, String> req) {
        String username = req.get("username");
        String password = req.get("password");
        Map<String, String> res = new HashMap<>();
        if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
            res.put("status", "error");
            res.put("message", "Username and password cannot be empty");
            return res;
        }
        if (users.containsKey(username)) {
            res.put("status", "error");
            res.put("message", "Username already exists");
            return res;
        }
        users.put(username, password);
        saveUsers();

        // assign user to a server pair (round robin)
        List<String> keys = new ArrayList<>(serverPairs.keySet());
        if (keys.isEmpty()) {
            res.put("status", "error");
            res.put("message", "No chat servers available");
            return res;
        }
        int idx = Math.abs(rr.getAndIncrement()) % keys.size();
        String assignedPair = keys.get(idx);
        userAssignments.put(username, assignedPair);
        saveAssignments();

        res.put("status", "success");
        res.put("user", username);
        res.put("assignedPair", assignedPair);
        return res;
    }

    @PostMapping("/login")
    public synchronized Map<String, String> login(@RequestBody Map<String, String> req) {
        String username = req.get("username");
        String password = req.get("password");
        Map<String, String> res = new HashMap<>();
        if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
            res.put("status", "error");
            res.put("message", "Username and password cannot be empty");
            return res;
        }
        if (!users.containsKey(username) || !users.get(username).equals(password)) {
            res.put("status", "error");
            res.put("message", "Invalid username or password");
            return res;
        }

        // find user's assigned pair
        String pairId = userAssignments.get(username);
        if (pairId == null) {
            // assign if missing (can happen for pre-existing users)
            List<String> keys = new ArrayList<>(serverPairs.keySet());
            if (keys.isEmpty()) {
                res.put("status", "error");
                res.put("message", "No chat servers available");
                return res;
            }
            pairId = keys.get(Math.abs(rr.getAndIncrement()) % keys.size());
            userAssignments.put(username, pairId);
            saveAssignments();
        }

        ServerPair pair = serverPairs.get(pairId);
        String activeMaster = pair != null ? pair.activeMaster : null;
        if (activeMaster == null) {
            // fallback to first available master
            activeMaster = pair != null ? (pair.master != null ? pair.master : pair.slave) : "http://localhost:9090";
        }

        res.put("status", "success");
        res.put("user", username);
        res.put("chatServerIp", activeMaster);
        return res;
    }

    // endpoint to get all usernames (keeps backward compatibility with frontend)
    @GetMapping("/users")
    public synchronized List<String> getAllUsers() {
        return new ArrayList<>(users.keySet());
    }

    // endpoint for frontend to re-resolve chat server for the user (in case of failover)
    @GetMapping("/resolve/{username}")
    public synchronized Map<String,String> resolveChatServer(@PathVariable String username) {
        Map<String,String> res = new HashMap<>();
        String pairId = userAssignments.get(username);
        if (pairId == null) { res.put("error", "no assignment"); return res; }
        ServerPair pair = serverPairs.get(pairId);
        if (pair == null) { res.put("error", "invalid pair"); return res; }
        res.put("chatServerIp", pair.activeMaster);
        return res;
    }

    // admin: list servers and status
    @GetMapping("/servers")
    public synchronized Collection<ServerPair> getServers() {
        return serverPairs.values();
    }

    // admin: add new pair (pairId, masterUrl, slaveUrl)
    @PostMapping("/servers/add")
    public synchronized Map<String,String> addServer(@RequestBody Map<String,String> req) {
        String pairId = req.get("pairId");
        String master = req.get("master");
        String slave = req.get("slave");
        Map<String,String> r = new HashMap<>();
        if (pairId == null || master == null) { r.put("status","error"); r.put("message","pairId and master required"); return r; }
        ServerPair p = new ServerPair(pairId, master, slave);
        p.setActiveMaster(master);
        serverPairs.put(pairId, p);
        saveServers();
        r.put("status","success");
        return r;
    }

    // ---- Health check + failover ----
    @Scheduled(fixedDelay = 5000) // every 5 seconds
    public void healthCheck() {
        System.out.println("[DEBUG] Running healthCheck...");
        for (ServerPair pair : serverPairs.values()) {
            boolean masterAlive = ping(pair.master);
            boolean slaveAlive = pair.slave != null && ping(pair.slave);

            System.out.println("[DEBUG] Pair: " + pair.pairId);
            System.out.println("        Master: " + pair.master + " alive? " + masterAlive);
            System.out.println("        Slave: " + pair.slave + " alive? " + slaveAlive);

            if (!masterAlive && slaveAlive) {
                // promote slave
                System.out.println("[DEBUG] Promoting slave to active master");
                pair.setActiveMaster(pair.slave);
            } else if (masterAlive) {
                pair.setActiveMaster(pair.master);
            } else {
                // both dead or no slave alive - leave activeMaster null
                System.out.println("[WARN] Both master and slave down for pair " + pair.pairId);
                pair.setActiveMaster(null);
            }
            System.out.println("        ActiveMaster now: " + pair.activeMaster);
        }
        saveServers();
    }

    private boolean ping(String url) {
        if (url == null) return false;
        try {
            String health = url + "/chat/health";
            ResponseEntity<String> resp = rest.getForEntity(health, String.class);
            System.out.println("[DEBUG] Ping " + url + " -> " + resp.getStatusCodeValue() + " : " + resp.getBody());
            return resp.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            System.out.println("[DEBUG] Ping failed for " + url + ": " + e.getMessage());
            return false;
        }
    }

    // ---- small helper classes ----
    public static class ServerPair {
        public String pairId;
        public String master;
        public String slave;
        public String activeMaster;

        public ServerPair() {}
        public ServerPair(String pairId, String master, String slave) {
            this.pairId = pairId; this.master = master; this.slave = slave; this.activeMaster = master;
        }

        public void setActiveMaster(String m) { this.activeMaster = m; }
    }
}
