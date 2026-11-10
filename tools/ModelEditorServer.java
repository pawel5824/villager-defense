import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executors;

/**
 * Narzędzie deweloperskie (NIE jest częścią pluginu ani moda).
 *
 * Mały serwer plików dla edytora modeli. Po co, skoro model-editor.html działa też
 * z pliku (file://):
 *   • po http:// przeglądarka może wczytywać pliki packa (fetch), np. tekstury do
 *     porównania w generatorze paczki,
 *   • File System Access API (przycisk „Zapisz do folderu packa”) działa pewniej,
 *   • localStorage jest przypisany do adresu — autozapis sceny nie miesza się
 *     z zapisem przy otwarciu z pliku.
 *
 * Użycie (z katalogu projektu):
 *   java tools/ModelEditorServer.java [port]        — domyślnie 52777
 *   Otwórz: http://127.0.0.1:52777/model-editor.html
 *
 * Serwer podaje wyłącznie pliki z katalogu projektu, tylko na 127.0.0.1.
 */
public class ModelEditorServer {

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 52777;
        Path root = Paths.get("").toAbsolutePath().normalize();

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/", ex -> serve(root, ex));
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();

        System.out.println("Edytor modeli: http://127.0.0.1:" + port + "/model-editor.html");
        System.out.println("Katalog: " + root + "   (Ctrl+C kończy pracę)");
    }

    private static void serve(Path root, HttpExchange ex) {
        try {
            String path = ex.getRequestURI().getPath();
            if (path.equals("/")) path = "/model-editor.html";
            Path file = root.resolve(path.substring(1)).normalize();
            if (!file.startsWith(root) || !Files.isRegularFile(file)) {
                ex.sendResponseHeaders(404, -1);
                return;
            }
            byte[] body = Files.readAllBytes(file);
            ex.getResponseHeaders().add("Content-Type", contentType(path));
            ex.getResponseHeaders().add("Cache-Control", "no-store");
            ex.sendResponseHeaders(200, body.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(body);
            }
        } catch (IOException e) {
            try { ex.sendResponseHeaders(500, -1); } catch (IOException ignored) { }
        } finally {
            ex.close();
        }
    }

    private static String contentType(String path) {
        if (path.endsWith(".html")) return "text/html; charset=utf-8";
        if (path.endsWith(".json") || path.endsWith(".mcmeta")) return "application/json; charset=utf-8";
        if (path.endsWith(".js")) return "text/javascript; charset=utf-8";
        if (path.endsWith(".png")) return "image/png";
        if (path.endsWith(".txt") || path.endsWith(".md")) return "text/plain; charset=utf-8";
        return "application/octet-stream";
    }
}
