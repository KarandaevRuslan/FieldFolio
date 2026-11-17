import java.lang.instrument.Instrumentation;
import java.security.Permission;

/** Test-only guard: fixtures may use loopback; real APIs and DNS must never be contacted. */
@SuppressWarnings("removal")
public final class OfflineNetworkAgent {
  public static void premain(String arguments, Instrumentation instrumentation) {
    System.setSecurityManager(new SecurityManager() {
      @Override public void checkPermission(Permission permission) {}
      @Override public void checkConnect(String host, int port) {
        if (!"localhost".equals(host) && !"127.0.0.1".equals(host)
            && !"::1".equals(host) && !"0.0.0.0".equals(host)) {
          throw new SecurityException("External networking is disabled in tests");
        }
      }
      @Override public void checkConnect(String host, int port, Object context) {
        checkConnect(host, port);
      }
    });
  }
}
