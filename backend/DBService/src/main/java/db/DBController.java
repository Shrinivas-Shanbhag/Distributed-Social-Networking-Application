package db;

import org.springframework.web.bind.annotation.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.nio.file.Paths;
import java.util.*;

@RestController
@RequestMapping("/db")
public class DBController {
    private static final String BASE = Paths.get("").toAbsolutePath().toString();
    private static final String FOLLOWERS_FILE = BASE + "/followers.json";
    private static final String CHATS_FILE = BASE + "/chats.json";
    private static final String POSTS_FILE = BASE + "/posts.json";

    private final ObjectMapper mapper = new ObjectMapper();

    // followers: username -> set of target users that username follows
    private Map<String, Set<String>> followers = new HashMap<>();
    // chats: username -> map<otherUser, list<ChatMessage>>
    private Map<String, Map<String, List<Map<String,Object>>>> chats = new HashMap<>();
    // posts timeline
    private List<Map<String,Object>> posts = new ArrayList<>();

    public DBController() {
        loadAll();
    }

    private synchronized void loadAll() {
        try { File f = new File(FOLLOWERS_FILE); if (f.exists()) followers = mapper.readValue(f, new TypeReference<>(){}); }
        catch(Exception e){ e.printStackTrace(); followers = new HashMap<>(); }
        try { File f = new File(CHATS_FILE); if (f.exists()) chats = mapper.readValue(f, new TypeReference<>(){}); }
        catch(Exception e){ e.printStackTrace(); chats = new HashMap<>(); }
        try { File f = new File(POSTS_FILE); if (f.exists()) posts = mapper.readValue(f, new TypeReference<>(){}); }
        catch(Exception e){ e.printStackTrace(); posts = new ArrayList<>(); }
    }

    private synchronized void saveFollowers() {
        try { mapper.writerWithDefaultPrettyPrinter().writeValue(new File(FOLLOWERS_FILE), followers); }
        catch(Exception e){ e.printStackTrace(); }
    }
    private synchronized void saveChats() {
        try { mapper.writerWithDefaultPrettyPrinter().writeValue(new File(CHATS_FILE), chats); }
        catch(Exception e){ e.printStackTrace(); }
    }
    private synchronized void savePosts() {
        try { mapper.writerWithDefaultPrettyPrinter().writeValue(new File(POSTS_FILE), posts); }
        catch(Exception e){ e.printStackTrace(); }
    }

    // follow/unfollow
    @PostMapping("/follow")
    public synchronized void followAction(@RequestBody Map<String,String> req) {
        String action = req.get("action");
        String currentUser = req.get("currentUser");
        String targetUser = req.get("targetUser");

        // Ensure both users exist
        followers.putIfAbsent(currentUser, new HashSet<>());
        followers.putIfAbsent(targetUser, new HashSet<>());

        if ("follow".equalsIgnoreCase(action)) {
            followers.get(currentUser).add(targetUser);
        } else {
            followers.get(currentUser).remove(targetUser);
        }
        saveFollowers();
    }

    // get users list (other users plus followed flag)
    @GetMapping("/users")
    public synchronized List<Map<String,Object>> getUsers(@RequestParam String currentUser) {
        Set<String> allUsers = new HashSet<>(followers.keySet());
        // Include all users who are targets of follows (in case they haven't followed anyone yet)
        for (Set<String> targets : followers.values()) {
            allUsers.addAll(targets);
        }

        // Ensure currentUser exists
        allUsers.add(currentUser);

        Set<String> following = followers.getOrDefault(currentUser, new HashSet<>());
        List<Map<String,Object>> res = new ArrayList<>();
        for (String u : allUsers) {
            if (u.equals(currentUser)) continue;
            Map<String,Object> m = new HashMap<>();
            m.put("username", u);
            m.put("followed", following.contains(u));
            res.add(m);
        }
        return res;
    }

    // chats persistence
    @PostMapping("/chats")
    public synchronized void persistChat(@RequestBody Map<String,Object> msg) {
        String from = (String) msg.get("from");
        String to = (String) msg.get("to");
        long timestamp = (Long) msg.get("timestamp");

        // Ensure users exist in followers map
        followers.putIfAbsent(from, new HashSet<>());
        followers.putIfAbsent(to, new HashSet<>());

        chats.putIfAbsent(from, new HashMap<>());
        chats.get(from).putIfAbsent(to, new ArrayList<>());
        chats.get(from).get(to).add(msg);

        chats.putIfAbsent(to, new HashMap<>());
        chats.get(to).putIfAbsent(from, new ArrayList<>());
        chats.get(to).get(from).add(msg);

        saveChats();
    }

    @GetMapping("/chats")
    public synchronized Map<String, List<Map<String,Object>>> getChats(@RequestParam String username) {
        Map<String, List<Map<String,Object>>> c = chats.getOrDefault(username, new HashMap<>());
        return c;
    }

    // posts
    @PostMapping("/posts")
    public synchronized void persistPost(@RequestBody Map<String,Object> postMsg) {
        posts.add(postMsg);
        savePosts();
    }

    @GetMapping("/timeline")
    public synchronized List<Map<String,Object>> getTimeline(@RequestParam String currentUser) {
        Set<String> following = followers.getOrDefault(currentUser, new HashSet<>());
        List<Map<String,Object>> res = new ArrayList<>();
        for (Map<String,Object> p : posts) {
            String from = (String) p.get("from");
            if (from.equals(currentUser) || following.contains(from)) {
                res.add(p);
            }
        }
        return res;
    }

    // return followers of a specific user (useful when broadcasting)
    @GetMapping("/followersOf")
    public synchronized List<String> followersOf(@RequestParam String user) {
        List<String> res = new ArrayList<>();
        for (Map.Entry<String, Set<String>> e : followers.entrySet()) {
            if (e.getValue().contains(user)) res.add(e.getKey());
        }
        return res;
    }
}
