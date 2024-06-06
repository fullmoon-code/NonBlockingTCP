import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;


public class NonBlockingServer {

    private static final int PORT = 8888; // 服务器监听的端口
    private static final int BUFFER_LENGTH = 1024;

    public static void main(String[] args) {
        // 使用 try-with-resources 语句自动关闭资源
        try (Selector selector = Selector.open();
             //创建一个ServerSocketChannel，这是一个可以监听新进来的TCP连接的通道
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
        // 从SelectionKey中获取与此键关联的Channel，并将其转换为ServerSocketChannel
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
        // 接受新的客户端连接，并返回与客户端通讯的SocketChannel
        SocketChannel socketChannel = serverSocketChannel.accept();
        // 将新接受的SocketChannel配置为非阻塞模式
        socketChannel.configureBlocking(false);
        // 将SocketChannel注册到选择器，并指定对读操作感兴趣
        socketChannel.register(key.selector(), SelectionKey.OP_READ);
        // 获取客户端的地址和端口，并打印出来
        System.out.println("Accepted connection from " + socketChannel.getRemoteAddress());
    }


    private static void handleRead(SelectionKey key) {
        // 从SelectionKey中获取对应的SocketChannel，这个通道用于网络通信
        SocketChannel socketChannel = (SocketChannel) key.channel();
        // 分配一个指定大小的ByteBuffer，用于存放从通道中读取的数据
        ByteBuffer buffer = ByteBuffer.allocate(BUFFER_LENGTH);

        try {
            // 从SocketChannel中读取数据到ByteBuffer中，并返回读取的字节数
            int bytesRead = socketChannel.read(buffer);
            if (bytesRead > 0) {
                // 将缓冲区转换为读模式
                buffer.flip();
                short type = buffer.getShort();
                switch (type) {
                    case 1: // Initialization 报文
                        handleInitialization(socketChannel, buffer);
                        break;
                    case 3: // reverseRequest 报文
                        handleReverseRequest(socketChannel, buffer);
                        break;
                    default:
                        // 如果报文类型不是已知的类型，则不做任何处理
                        break;
                }
            } else if (bytesRead == -1) {
                // 如果读取到的字节数为-1，表示对方已经关闭了连接
                System.out.println("关闭连接：" + socketChannel.getRemoteAddress());
                // 关闭SocketChannel
                socketChannel.close();
                // 取消SelectionKey，这样Selector就不会再监控这个通道的任何操作了
                key.cancel();
            }
        } catch (IOException e) {
            System.out.println("读取过程中发生异常：" + e.getMessage());
            // 取消SelectionKey，这样Selector就不会再监控这个通道的任何操作了
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
    // 处理反转请求的函数
    private static void handleReverseRequest(SocketChannel socket_channel, ByteBuffer buffer) throws IOException {
        // 获取远程客户端的地址信息
        InetSocketAddress remote_address = (InetSocketAddress) socket_channel.getRemoteAddress();
        String remote_ip = remote_address.getAddress().getHostAddress(); // 获取远程客户端的IP地址
        int remote_port = remote_address.getPort(); // 获取远程客户端的端口号

        // 解析接收到的报文
        int length = buffer.getInt(); // 获取数据长度
        int block_no = buffer.getInt(); // 获取块编号
        byte[] data = new byte[length]; // 分配字节数组用于存储数据
        buffer.get(data); // 从缓冲区中读取数据

        // 打印接收到的反转请求报文信息
        System.out.println("接收到来自 " + remote_ip + ":" + remote_port + " 的 reverseRequest 报文，第" + block_no + "块: " +  new String(data));

        // 反转数据
        byte[] reversed_data = reverseData(data); // 调用反转数据的函数
        // 打印即将发送的反转回答报文信息
        System.out.println("将要向 " + remote_ip + ":" + remote_port + " 发送 reverseAnswer 报文，第" + block_no + "块: " + new String(reversed_data));

        // 构造反转回答报文
        ByteBuffer answer_buffer = ByteBuffer.allocate(10 + reversed_data.length); // 分配缓冲区大小为10字节头部加上反转后的数据长度
        answer_buffer.putShort((short) 4); // 消息类型，类型4表示反转回答
        answer_buffer.putInt(reversed_data.length); // 数据长度
        answer_buffer.putInt(block_no); // 块编号
        answer_buffer.put(reversed_data); // 反转后的数据
        answer_buffer.flip(); // 切换缓冲区为读模式
        socket_channel.write(answer_buffer); // 发送反转回答报文
    }

    // 用于反转字节数组中的数据
    private static byte[] reverseData(byte[] data) {
        byte[] reversed_data = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            reversed_data[i] = data[data.length - 1 - i];
        }
        return reversed_data;
    }
}
