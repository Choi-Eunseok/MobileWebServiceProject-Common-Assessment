import os
import socket
from datetime import datetime

class SocketServer:
    def __init__(self):
        self.bufsize = 1024  # 버퍼 크기 설정
        with open('./response.bin', 'rb') as file:
            self.RESPONSE = file.read()  # 응답 파일 읽기

        self.DIR_PATH = './request'
        self.createDir(self.DIR_PATH)

    def createDir(self, path):
        """디렉토리 생성"""
        try:
            if not os.path.exists(path):
                os.makedirs(path)
        except OSError:
            print("Error: Failed to create the directory.")

    def run(self, ip, port):
        """서버 실행"""
        # 소켓 생성
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self.sock.bind((ip, port))
        self.sock.listen(10)
        print("Start the socket server...")
        print("\"Ctrl+C\" for stopping the server!\r\n")

        try:
            while True:
                # 클라이언트의 요청 대기
                clnt_sock, req_addr = self.sock.accept()
                clnt_sock.settimeout(5.0)  # 타임아웃 설정 (5초)
                print("Request message...\r\n")

                # 요청 메시지 수신
                request = b""
                try:
                    while b"\r\n\r\n" not in request:
                        data = clnt_sock.recv(self.bufsize)
                        if not data:
                            break
                        request += data
                except socket.timeout:
                    pass

                # 요청 메시지를 파일로 저장 (예: request/2024-10-16-01-12-45.bin)
                filename = datetime.now().strftime("%Y-%m-%d-%H-%M-%S") + ".bin"
                with open(os.path.join(self.DIR_PATH, filename), "wb") as f:
                    f.write(request)
                print(request.decode("utf-8", errors="replace"))

                # 응답 전송
                clnt_sock.sendall(self.RESPONSE)

                # 클라이언트 소켓 닫기
                clnt_sock.close()
        except KeyboardInterrupt:
            print("\r\nStop the server...")

        # 서버 소켓 닫기
        self.sock.close()

if __name__ == "__main__":
    server = SocketServer()
    server.run("127.0.0.1", 8000)