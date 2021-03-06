import com.google.common.collect.Maps;
import org.json.JSONException;
import org.json.JSONObject;

import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.*;

@ServerEndpoint("/chat")
public class SocketServer
{
    // set to store all the live sessions
    private static final Set<Session> sessions = Collections.synchronizedSet(new HashSet<>());

    // Mapping between sessions and person name
    private static final HashMap<String, String> nameSessionPair = new HashMap<>();

    private JSONUtils jsonUtils = new JSONUtils();

    // Getting query params
    public static Map<String, String> getQueryMap(String query)
    {
        Map<String, String> map = Maps.newHashMap();
        if (query != null)
        {
            String[] params = query.split("&");
            for (String param : params)
            {
                String[] nameval = param.split("=");
                map.put(nameval[0], nameval[1]);
            }
        }
        return map;
    }

    /**
     * Called when a socket connection opened
     *
     * @param session
     */
    @OnOpen
    public void onOpen(Session session)
    {
        System.out.println(session.getId() + " has opened a connection");
        Map<String, String> queryParams = getQueryMap(session.getQueryString());
        String name = "";
        if (queryParams.containsKey("name"))
        {
            // Getting client name via query param
            name = queryParams.get("name");
            try
            {
                name = URLDecoder.decode(name, "UTF-8");
            }
            catch (UnsupportedEncodingException e)
            {
                e.printStackTrace();
            }

            // Mapping client name and session id
            nameSessionPair.put(session.getId(), name);
        }

        // Adding session to session list
        sessions.add(session);

        try
        {
            // Sending session id to the client that just connected
            session.getBasicRemote().sendText(jsonUtils.getClientDetailsJson(session.getId(), "Your session details"));
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        // Notifying all the clients about new person joined
        sendMessageToAll(session.getId(), name, " joined conversation!", true, false);
    }

    /**
     * method called when new message received from any client
     *
     * @param message JSON message from client
     * @param session
     */
    @OnMessage
    public void onMessage(String message, Session session)
    {
        System.out.println("Message from " + session.getId() + ": " + message);
        String msg = null;

        // Parsing the json and getting message
        try
        {
            JSONObject jObj = new JSONObject(message);
            msg = " " + jObj.getString("message");
        }
        catch (JSONException e)
        {
            e.printStackTrace();
        }

        // Sending the message to all clients
        sendMessageToAll(session.getId(), nameSessionPair.get(session.getId()), msg, false, false);
    }

    /**
     * Method called when a connection is closed
     *
     * @param session
     */
    @OnClose
    public void onClose(Session session)
    {
        System.out.println("Session " + session.getId() + " has ended");

        // Getting the client name that exited
        String name = nameSessionPair.get(session.getId());

        // Removing the session from sessions list
        sessions.remove(session);

        // Notifying all the clients about person exit
        sendMessageToAll(session.getId(), name, " left conversation!", false, true);
    }

    /**
     * Method to send message to all clients
     *
     * @param sessionId
     * @param name
     * @param message     message to be send to clients
     * @param isNewClient flag to identify that message is about new person joined
     * @param isExit      flag to identify that a person left the conversation
     */
    private void sendMessageToAll(String sessionId, String name, String message, boolean isNewClient, boolean isExit)
    {
        // Looping through all the sessions and sending the message individually
        for (Session s : sessions)
        {
            String json = null;

            if (isNewClient)
            {
                // Checking if the message is about new client joined
                json = jsonUtils.getNewClientJson(sessionId, name, message, sessions.size());
            }
            else if (isExit)
            {
                // Checking if the person left the conversation
                json = jsonUtils.getClientExitJson(sessionId, name, message, sessions.size());
            }
            else
            {
                // Normal chat conversation message
                json = jsonUtils.getSendAllMessageJson(sessionId, name, message);
            }

            try
            {
                System.out.println("Sending Message To: " + s.getId() + ", " + json);
                s.getBasicRemote().sendText(json);
            }
            catch (IOException e)
            {
                System.out.println("error in sending. " + s.getId() + ", " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}
