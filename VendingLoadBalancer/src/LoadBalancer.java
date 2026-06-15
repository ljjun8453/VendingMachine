import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class LoadBalancer {
    // 로드밸런서의 포트번호 : 9000
    private static final int LISTEN_PORT = 9000;

//  로컬로 돌릴 때
    private static final String[] SERVER_HOSTS = {
            "127.0.0.1",
            "127.0.0.1"
    };

//  서버로 돌릴 때
//    private static final String[] SERVER_HOSTS = {
//            "192.168.0.14",
//            "192.168.0.14"
//    };

    // 서버1과 서버2의 포트번호
    private static final int[] SERVER_PORTS = {
            9001,
            9002
    };
    // 백업서버의 주소와 포트번호
    private static final String BACKUP_SERVER_HOST = "127.0.0.1";
    private static final int BACKUP_SERVER_PORT = 9003;

    private static final boolean[] serverAlive = {
            true,
            true
    };// 서버1, 서버2의 동작 상태 저장 배열

    private static boolean backupAlive = true;// 백업 서버의 동작 상태
    private static int nextServerIndex = 0;// 라운드 로빈 방식으로 다음 연결 서버를 선택하기 위한 인덱스

    // 로드밸런서를 시작하고 클라이언트 접속을 백엔드 서버로 중계한다.
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
    // 백엔드 서버 상태를 주기적으로 검사하는 헬스체크 스레드를 시작한다.
    private static void startHealthCheckThread() {
        LoadBalancerHealthThread thread = new LoadBalancerHealthThread();
        thread.setDaemon(true);
        thread.start();
    }

    private static class LoadBalancerHealthThread extends Thread {
        // 서버1, 서버2, 백업서버의 상태를 주기적으로 확인한다.
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
                        System.out.println("[로드밸런서] 서버 장애 감지, 백업서버 작동 중");
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
    // 지정된 서버에 PING을 전송하고 PONG 응답 여부로 서버 상태를 판단한다.
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
    // 라운드 로빈 방식으로 다음 연결 대상 서버 인덱스를 반환한다.
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
        // 클라이언트 소켓을 저장하여 로드밸런싱 처리 스레드를 생성한다.
        LoadBalancerThread(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }
        // 클라이언트와 선택된 백엔드 서버 사이의 양방향 중계를 수행한다.
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
        // 상태가 정상인 백엔드 서버 또는 백업 서버에 연결한다.
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
                    selectedServerName = "원래 대상 서버"
                            + (targetIndex + 1)
                            + "(" + BACKUP_SERVER_HOST
                            + ":"
                            + BACKUP_SERVER_PORT
                            + ")"
                            + " 대신 백업서버로 연결됨";
                    return socket;
                }

                backupAlive = false;
            }

            return null;
        }
        // 지정된 호스트와 포트로 TCP 연결을 시도한다.
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
        // 한 방향의 입력 스트림 데이터를 출력 스트림으로 전달하는 중계 스레드를 생성한다.
        ForwardThread(InputStream inputStream, OutputStream outputStream, Socket inputSocket, Socket outputSocket) {
            this.inputStream = inputStream;
            this.outputStream = outputStream;
            this.inputSocket = inputSocket;
            this.outputSocket = outputSocket;
        }
        // 입력 스트림에서 읽은 데이터를 출력 스트림으로 계속 전달한다.
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
    // BufferedReader 자원을 안전하게 종료한다.
    private static void closeReader(BufferedReader reader) {
        try {
            if (reader != null) {
                reader.close();
            }
        } catch (IOException e) {
        }
    }
    // PrintWriter 자원을 안전하게 종료한다.
    private static void closeWriter(PrintWriter writer) {
        if (writer != null) {
            writer.close();
        }
    }
    // Socket 자원을 안전하게 종료한다.
    private static void closeSocket(Socket socket) {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
        }
    }
}
