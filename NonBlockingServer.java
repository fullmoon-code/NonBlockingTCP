import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class NonBlockingServer {

    private static final int PORT = 8888; // 服务器监听的端口
    // 在 NonBlockingServer 类中添加以下代码
    private static final Logger logger = Logger.getLogger(NonBlockingServer.class.getName());
    public static void main(String[] args) {
        // 配置日志记录
        try {
            FileHandler fileHandler = new FileHandler("server.log", true);
            fileHandler.setFormatter(new SimpleFormatter());
            logger.addHandler(fileHandler);
        } catch (IOException e) {
            e.printStackTrace();
        }
        // 使用 try-with-resources 语句自动关闭资源
        try (Selector selector = Selector.open();
             ServerSocketChannel server_socket_channel = ServerSocketChannel.open()) {
            // 绑定端口
            server_socket_channel.bind(new InetSocketAddress(PORT));
            // 配置为非阻塞模式
            server_socket_channel.configureBlocking(false);
            // 将通道注册到选择器，监听接收事件
            server_socket_channel.register(selector, SelectionKey.OP_ACCEPT);
            System.out.println("服务器启动，监听端口：" + PORT);

            // 主循环，处理就绪的通道
            while (true) {
                // 阻塞等待至少有一个通道准备好进行 I/O 操作
                selector.select();

                // 获取准备好的通道的键集
                Set<SelectionKey> selected_keys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = selected_keys.iterator();

                // 遍历每个键并处理相应的事件
                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    if (key.isAcceptable()) {
                        // 处理新连接事件
                        handleAccept(key);
                    } else if (key.isReadable()) {
                        // 处理读事件
                        handleRead(key);
                    }
                    // 移除已处理的键，避免重复处理
                    iterator.remove();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 处理新连接
    private static void handleAccept(SelectionKey key) throws IOException {
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
        SocketChannel socketChannel = serverSocketChannel.accept();
        socketChannel.configureBlocking(false);
        socketChannel.register(key.selector(), SelectionKey.OP_READ);
        logger.info("Accepted new connection from " + socketChannel.getRemoteAddress());
    }

    private static void handleRead(SelectionKey key) {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        ByteBuffer buffer = ByteBuffer.allocate(1024);

        try {
            int bytesRead = socketChannel.read(buffer);
            if (bytesRead > 0) {
                buffer.flip();
                short type = buffer.getShort(); // 获取报文类型
                switch (type) {
                    case 1: // Initialization 报文
                        handleInitialization(socketChannel, buffer);
                        break;
                    case 3: // reverseRequest 报文
                        handleReverseRequest(socketChannel, buffer);
                        break;
                    default:
                        logger.warning("Unknown message type: " + type);
                        break;
                }
            } else if (bytesRead == -1) {
                logger.info("Connection closed by " + socketChannel.getRemoteAddress());
                socketChannel.close();
                key.cancel();
            }
        } catch (IOException e) {
            logger.severe("Connection error: " + e.getMessage());
            key.cancel();
        }
    }


    // 处理 Initialization 报文
    private static void handleInitialization(SocketChannel socket_channel, ByteBuffer buffer) throws IOException {
        int num_blocks = buffer.getInt(); // 获取块数
        System.out.println("接收到 Initialization 报文，将要接收的块数：" + num_blocks);

        // 发送 agree 报文
        ByteBuffer agree_buffer = ByteBuffer.allocate(2 + 3);
        agree_buffer.putShort((short) 2); // Type
        agree_buffer.flip();
        socket_channel.write(agree_buffer);
    }

    // 处理 reverseRequest 报文
    private static void handleReverseRequest(SocketChannel socket_channel, ByteBuffer buffer) throws IOException {
        InetSocketAddress remote_address = (InetSocketAddress) socket_channel.getRemoteAddress();
        String remote_ip = remote_address.getAddress().getHostAddress();
        int remote_port = remote_address.getPort();

        int length = buffer.getInt(); // 获取数据长度
        int block_no = buffer.getInt(); // 获取块编号
        byte[] data = new byte[length];
        buffer.get(data);
        String received_data = new String(data);

        System.out.println("接收到来自 " + remote_ip + ":" + remote_port + " 的 reverseRequest 报文，第" + block_no + "块: " + received_data);
        // 反转数据
        byte[] reversed_data = reverseData(data);
        System.out.println("将要向 " + remote_ip + ":" + remote_port + " 发送 reverseAnswer 报文，第" + block_no + "块: " + new String(reversed_data));

        // 构造 reverseAnswer 报文
        ByteBuffer answer_buffer = ByteBuffer.allocate(10 + 3 + reversed_data.length);
        answer_buffer.putShort((short) 4); // Type
        answer_buffer.putInt(reversed_data.length); // Length
        answer_buffer.putInt(block_no); // 块编号
        answer_buffer.put(reversed_data); // 反转后的数据
        answer_buffer.flip();
        socket_channel.write(answer_buffer);
    }

    // 反转字节数组
    private static byte[] reverseData(byte[] data) {
        byte[] reversed_data = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            reversed_data[i] = data[data.length - 1 - i];
        }
        return reversed_data;
    }
}
