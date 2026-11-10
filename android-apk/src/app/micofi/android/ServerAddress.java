package app.micofi.android;

import java.net.URI;

/** Pure validation shared by setup, navigation and local JVM checks. */
final class ServerAddress {
    static String normalize(String value) {
        if (value == null) return "";
        String result=value.trim().replaceAll("/+$", "");
        if (!result.contains("://") && valid("http://"+result)) result="http://"+result;
        return result;
    }
    static boolean production(String value) {
        return valid(value) && "https".equalsIgnoreCase(URI.create(value).getScheme());
    }
    static boolean local(String value) {
        return valid(value) && valid(value.replaceFirst("(?i)^https://", "http://"));
    }
    static boolean valid(String value) {
        try {
            URI u = new URI(value);
            String host = u.getHost();
            if (host == null || host.isEmpty() || u.getRawUserInfo() != null
                    || u.getRawQuery() != null || u.getRawFragment() != null
                    || !(u.getPath() == null || u.getPath().isEmpty() || u.getPath().equals("/"))
                    || u.getPort() == 0 || u.getPort() > 65535) return false;
            if ("https".equalsIgnoreCase(u.getScheme())) return true;
            if (!"http".equalsIgnoreCase(u.getScheme())) return false;
            String[] parts = host.split("\\.", -1);
            if (parts.length != 4) return false;
            int[] numbers = new int[4];
            for (int i = 0; i < 4; i++) {
                if (!parts[i].matches("0|[1-9][0-9]{0,2}")) return false;
                numbers[i] = Integer.parseInt(parts[i]);
                if (numbers[i] > 255) return false;
            }
            return numbers[0] == 10 || (numbers[0] == 192 && numbers[1] == 168)
                    || (numbers[0] == 172 && numbers[1] >= 16 && numbers[1] <= 31);
        } catch (Exception ignored) { return false; }
    }

    static boolean sameOrigin(String server, String url) {
        try {
            URI a = new URI(server), b = new URI(url);
            return a.getScheme().equalsIgnoreCase(b.getScheme())
                    && a.getHost().equalsIgnoreCase(b.getHost())
                    && port(a) == port(b) && b.getRawUserInfo() == null;
        } catch (Exception ignored) { return false; }
    }
    private static int port(URI u) {
        return u.getPort() != -1 ? u.getPort() : ("https".equalsIgnoreCase(u.getScheme()) ? 443 : 80);
    }

    static String bundledPath(String path) {
        if (path == null || !path.startsWith("/") || path.contains("..") || path.contains("\\")
                || path.contains("%") || path.contains("\u0000")) return null;
        if (path.startsWith("/api/") || path.equals("/api")) return null;
        if (path.startsWith("/assets/") || path.matches("/[A-Za-z0-9_-]+\\.(js|css|ico|webmanifest)"))
            return "www" + path;
        // Angular routes have no extension. API and user images are never bundled.
        if (!path.substring(path.lastIndexOf('/') + 1).contains(".")) return "www/index.html";
        return null;
    }
}
