<%@ page import="java.net.URL,java.net.HttpURLConnection,java.io.InputStreamReader,java.io.BufferedReader" %>
<%
    try {
        String apiKey = System.getenv("API_KEY");
        if (apiKey == null) {
            out.println("API_KEY is null");
            return;
        }
        URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models?key=" + apiKey);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        String inputLine;
        while ((inputLine = in.readLine()) != null) {
            out.println(inputLine);
        }
        in.close();
    } catch (Exception e) {
        out.println("Error: " + e.getMessage());
    }
%>
