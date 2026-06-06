import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class LoadBalancer {
    private static final int LISTEN_PORT = 9000;

//    로컬로 돌릴 때
//    private static final String[] SERVER_HOSTS = {
//            "12.0.0.1",
//            "12.0.0.1"
//    };

//  서버로 돌릴 때
    private static final String[] SERVER_HOSTS = {
            "192.168.0.14",
            "192.168.0.14"
    };

    private static final int[] SERVER_PORTS = {
            9001,
            9002
    };

    private static final String BACKUP_SERVER_HOST = "127.0.0.1";
    private static final int BACKUP_SERVER_PORT = 9003;

    private static final boolean[] serverAlive = {
            true,
            true
    };

    private static boolean backupAlive = true;
    private static int nextServerIndex = 0;

    public static void main(String[] args) {
        ServerSocket listenSocket = null;

        startHealthCheckThread();

        try {
            listenSocket = new ServerSocket(LISTEN_PORT);
            System.out.println("[로드밸런서] TCP 로드밸런서 시작, 포트: " + LISTEN_PORT);

            while (true) {
                Socket clientSocket = listenSocket.accept();
                LoadBalancerThread thread = new LoadBalancerThread(clientSocket);
                thread.start();
            }
        } catch (IOException e) {
            System.out.println("[로드밸런서] 오류: " + e.getMessage());
        } finally {
            try {
                if (listenSocket != null) {
                    listenSocket.close();
                }
            } catch (IOException e) {
                System.out.println("[로드밸런서] 종료 오류: " + e.getMessage());
            }
        }
    }

    private static void startHealthCheckThread() {
        LoadBalancerHealthThread thread = new LoadBalancerHealthThread();
        thread.setDaemon(true);
        thread.start();
    }

    private static class LoadBalancerHealthThread extends Thread {
        public void run() {
            while (true) {
                serverAlive[0] = checkServer(SERVER_HOSTS[0], SERVER_PORTS[0]);
                serverAlive[1] = checkServer(SERVER_HOSTS[1], SERVER_PORTS[1]);
                backupAlive = checkServer(BACKUP_SERVER_HOST, BACKUP_SERVER_PORT);

                System.out.println("[로드밸런서] 서버1 상태: " + serverAlive[0]);
                System.out.println("[로드밸런서] 서버2 상태: " + serverAlive[1]);
                System.out.println("[로드밸런서] 백업서버 상태: " + backupAlive);

                if (!serverAlive[0] || !serverAlive[1]) {
                    if (backupAlive) {
                        System.out.println("[로드밸런서] 서버 장애 감지, 백업서버 대체 가능");
                    } else {
                        System.out.println("[로드밸런서] 서버 장애 감지, 백업서버 연결 불가");
                    }
                }

                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }

    private static boolean checkServer(String host, int port) {
        Socket socket = null;
        BufferedReader in = null;
        PrintWriter out = null;

        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), 1000);
            socket.setSoTimeout(1000);

            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);

            out.println("PING");

            String response = in.readLine();

            if ("PONG".equals(response)) {
                return true;
            }

            return false;
        } catch (IOException e) {
            return false;
        } finally {
            closeReader(in);
            closeWriter(out);
            closeSocket(socket);
        }
    }

    private static synchronized int getNextServerIndex() {
        int index = nextServerIndex;

        nextServerIndex++;

        if (nextServerIndex >= SERVER_PORTS.length) {
            nextServerIndex = 0;
        }

        return index;
    }

    private static class LoadBalancerThread extends Thread {
        private Socket clientSocket;
        private Socket serverSocket;
        private String selectedServerName = "NONE";

        LoadBalancerThread(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        public void run() {
            try {
                serverSocket = connectBackendServer();

                if (serverSocket == null) {
                    System.out.println("[로드밸런서] 연결 가능한 서버 없음: " + clientSocket.getRemoteSocketAddress());
                    closeSocket(clientSocket);
                    return;
                }

                System.out.println("[로드밸런서] 클라이언트 연결: "
                        + clientSocket.getRemoteSocketAddress()
                        + " -> "
                        + selectedServerName);

                ForwardThread clientToServer = new ForwardThread(
                        clientSocket.getInputStream(),
                        serverSocket.getOutputStream(),
                        clientSocket,
                        serverSocket
                );

                ForwardThread serverToClient = new ForwardThread(
                        serverSocket.getInputStream(),
                        clientSocket.getOutputStream(),
                        serverSocket,
                        clientSocket
                );

                clientToServer.start();
                serverToClient.start();

                try {
                    clientToServer.join();
                } catch (InterruptedException e) {
                }

                try {
                    serverToClient.join();
                } catch (InterruptedException e) {
                }
            } catch (IOException e) {
                System.out.println("[로드밸런서] 중계 오류: " + e.getMessage());
            } finally {
                closeSocket(clientSocket);
                closeSocket(serverSocket);
                System.out.println("[로드밸런서] 접속 종료: " + selectedServerName);
            }
        }

        private Socket connectBackendServer() {
            int targetIndex = getNextServerIndex();
            Socket socket;

            if (serverAlive[targetIndex]) {
                socket = connectServer(SERVER_HOSTS[targetIndex], SERVER_PORTS[targetIndex]);

                if (socket != null) {
                    selectedServerName = "서버" + (targetIndex + 1)
                            + "(" + SERVER_HOSTS[targetIndex]
                            + ":"
                            + SERVER_PORTS[targetIndex]
                            + ")";
                    return socket;
                }

                serverAlive[targetIndex] = false;
            }

            if (backupAlive) {
                socket = connectServer(BACKUP_SERVER_HOST, BACKUP_SERVER_PORT);

                if (socket != null) {
                    selectedServerName = "백업서버 대신 연결됨, 원래 대상 서버"
                            + (targetIndex + 1)
                            + "(" + BACKUP_SERVER_HOST
                            + ":"
                            + BACKUP_SERVER_PORT
                            + ")";
                    return socket;
                }

                backupAlive = false;
            }

            return null;
        }

        private Socket connectServer(String host, int port) {
            Socket socket = null;

            try {
                socket = new Socket();
                socket.connect(new InetSocketAddress(host, port), 1000);
                return socket;
            } catch (IOException e) {
                closeSocket(socket);
                return null;
            }
        }
    }

    private static class ForwardThread extends Thread {
        private InputStream inputStream;
        private OutputStream outputStream;
        private Socket inputSocket;
        private Socket outputSocket;

        ForwardThread(InputStream inputStream, OutputStream outputStream, Socket inputSocket, Socket outputSocket) {
            this.inputStream = inputStream;
            this.outputStream = outputStream;
            this.inputSocket = inputSocket;
            this.outputSocket = outputSocket;
        }

        public void run() {
            byte[] buffer = new byte[4096];
            int length;

            try {
                while ((length = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, length);
                    outputStream.flush();
                }
            } catch (IOException e) {
            } finally {
                closeSocket(inputSocket);
                closeSocket(outputSocket);
            }
        }
    }

    private static void closeReader(BufferedReader reader) {
        try {
            if (reader != null) {
                reader.close();
            }
        } catch (IOException e) {
        }
    }

    private static void closeWriter(PrintWriter writer) {
        if (writer != null) {
            writer.close();
        }
    }

    private static void closeSocket(Socket socket) {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
        }
    }
}
