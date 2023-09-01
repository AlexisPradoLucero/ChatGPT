import gearth.extensions.ExtensionForm;
import gearth.extensions.ExtensionInfo;
import gearth.protocol.HMessage;
import gearth.protocol.HPacket;
import gearth.extensions.parsers.HEntity;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

@ExtensionInfo(
        Title = "ChatGPT",
        Description = "ChatGPT IA",
        Version = "1.0",
        Author = "AlexisPrado"
)

public class ChatGPT extends ExtensionForm {
    public String YourName;
    public int YourIndex = -1;
    private int chatPacketCount = 0;
    private int signPacketCount = 0;
    private long lastIncrementTime = 0;

    @Override
    protected void onShow() {
    }

    @Override
    protected void onHide() {
    }

    @Override
    protected void initExtension() {
        sendToServer(new HPacket("InfoRetrieve", HMessage.Direction.TOSERVER));
        intercept(HMessage.Direction.TOCLIENT, "Chat", this::InChat);
        intercept(HMessage.Direction.TOCLIENT, "Shout", this::InChat);
        intercept(HMessage.Direction.TOCLIENT, "UserObject", hMessage -> {
            // Gets ID and Name in order.
            hMessage.getPacket().readInteger();
            YourName = hMessage.getPacket().readString();
        });
        intercept(HMessage.Direction.TOCLIENT, "Users", hMessage -> {
            try {
                HPacket hPacket = hMessage.getPacket();
                HEntity[] roomUsersList = HEntity.parse(hPacket);
                for (HEntity hEntity : roomUsersList) {
                    if (YourName.equals(hEntity.getName())) {
                        YourIndex = hEntity.getIndex();
                    }
                }
            } catch (Exception exception) {
            }
        });
    }

    private void InChat(HMessage hMessage) {
        int index = hMessage.getPacket().readInteger();
        String prompt = hMessage.getPacket().readString();

        if (index != YourIndex && (prompt.startsWith(":gpt ") || prompt.startsWith("@red@:gpt ") || prompt.startsWith("@green@:gpt ") || prompt.startsWith("@purple@:gpt ") || prompt.startsWith("@blue@:gpt ") || prompt.startsWith("@cyan@:gpt "))) {
            String chatbotResponse = getChatbotResponse(prompt + ". your answer must be a maximum of 100 characters, mandatory, and in the language I asked you");

            if (chatPacketCount < 4) {
                if (chatbotResponse.length() > 100) {
                    chatbotResponse = "I can't write the complete answer because it was too long as it exceeds 100 characters.";
                }
                long currentMillis = System.currentTimeMillis();
                if (currentMillis - lastIncrementTime > 4000) {
                    chatPacketCount = 0;
                }

                lastIncrementTime = currentMillis;
                sendToServer(new HPacket("Chat", HMessage.Direction.TOSERVER, chatbotResponse, 0, 0));
                chatPacketCount++;
                System.out.println(chatPacketCount);
                System.out.println(chatbotResponse);
            } else {
                sendToServer(new HPacket("Sign", HMessage.Direction.TOSERVER, 13));
                signPacketCount++;
            }
            if (signPacketCount == 1) {
                startResetThread();
            }
        }
    }

    private void startResetThread() {
        new Thread(() -> {
            try {
                Thread.sleep(6000);
                chatPacketCount = 0;
                signPacketCount = 0;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private String getChatbotResponse(String userMessage) {
        String url = "https://chatbot-ji1z.onrender.com/chatbot-ji1z";
        HttpURLConnection connection = null;
        BufferedReader in = null;

        try {
            JSONObject data = new JSONObject();
            JSONArray messages = new JSONArray();
            JSONObject message = new JSONObject();
            message.put("role", "user");
            message.put("content", userMessage);
            messages.put(message);
            data.put("messages", messages);

            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
            connection.setConnectTimeout(10000); // 10 segundos de tiempo de conexión
            connection.setReadTimeout(20000);  // 20 segundos de tiempo de lectura

            String jsonInputString = data.toString();
            connection.getOutputStream().write(jsonInputString.getBytes());

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String inputLine;
                StringBuilder response = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }

                JSONObject responseJson = new JSONObject(response.toString());
                String content = responseJson.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content");

                return content;
            } else {
                return "Error sending the request. Status code:: " + responseCode;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return "Error in communication with the ChatGPT: " + e.getMessage();
        } catch (JSONException e) {
            e.printStackTrace();
            return "Error in handling the ChatGPT response: " + e.getMessage();
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (connection != null) {
                connection.disconnect();
            }

            // Agregar un tiempo de espera entre solicitudes para evitar sobrecargar el servidor
            try {
                Thread.sleep(700);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}