package com.evilmidget38;

import com.google.common.collect.ImmutableList;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.JSONParser;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
/**
 * Modification of https://gist.github.com/evilmidget38/df8dcd7855937e9d1e1f to work with the new name->UUID api
 *
 * @author https://github.com/evilmidget38
 * @author Kiskae
 */
public class UUIDFetcher implements Callable<Map<String, UUID>> {
    private static final String PROFILE_URL = "https://api.mojang.com/profiles/minecraft";
    private final JSONParser jsonParser = new JSONParser();
    private final List<List<String>> namesList;
    public UUIDFetcher(List<String> names) {
        final ImmutableList.Builder<List<String>> builder = ImmutableList.builder();
        int namesCopied = 0;
        while (namesCopied < names.size()) {
            builder.add(
                    ImmutableList.copyOf(
                            names.subList(
                                    namesCopied,
                                    Math.min(namesCopied + 50, names.size())
                            )
                    )
            );
            namesCopied += 50;
        }
        this.namesList = builder.build();
    }
    public Map<String, UUID> call() throws Exception {
        Map<String, UUID> uuidMap = new HashMap<>();
        for (List<String> names : this.namesList) {
            String body = buildBody(names);
            //System.out.printf("Requesting %s%n", body);
            HttpURLConnection connection = createConnection();
            writeBody(connection, body);
            JSONArray jsonArr = (JSONArray) jsonParser.parse(new InputStreamReader(connection.getInputStream()));
            //System.out.println("response:"+jsonArr);
            for (Object profile : jsonArr) {
                JSONObject jsonProfile = (JSONObject) profile;
                String id = (String) jsonProfile.get("id");
                String name = (String) jsonProfile.get("name");
                UUID uuid = UUID.fromString(id.substring(0, 8) + "-" + id.substring(8, 12) + "-" + id.substring(12, 16) + "-" + id.substring(16, 20) + "-" + id.substring(20, 32));
                uuidMap.put(name, uuid);
            }
            Thread.sleep(2000);
        }
        return uuidMap;
    }
    private static void writeBody(HttpURLConnection connection, String body) throws Exception {
        DataOutputStream writer = new DataOutputStream(connection.getOutputStream());
        writer.write(body.getBytes());
        writer.flush();
        writer.close();
    }
    private static HttpURLConnection createConnection() throws Exception {
        URL url = new URL(PROFILE_URL);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setUseCaches(false);
        connection.setDoInput(true);
        connection.setDoOutput(true);
        return connection;
    }
    private static String buildBody(List<String> names) {
        return JSONValue.toJSONString(names);
    }
    public static UUID getUUIDOf(String name) throws Exception {
        return new UUIDFetcher(Arrays.asList(name)).call().get(name);
    }
}