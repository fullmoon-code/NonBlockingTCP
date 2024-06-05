import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class NonBlockingClient {
    private static final int BUFFER_SIZE = 1024;

    public static void main(String[] args) {
        if (args.length < 5) {
            System.err.println("Usage: java NonBlockingClient <server_ip> <server_port> <l_min> <l_max> <file_path>");
            System.exit(1);
        }

        String server_ip = args[0];
        int server_port = Integer.parseInt(args[1]);
        int Lmin = Integer.parseInt(args[2]);
        int Lmax = Integer.parseInt(args[3]);
        String file_path = args[4];

        File file = new File(file_path);
        if (!file.exists() || !file.isFile()) {
            System.err.println("文件路径不合法！");
            System.exit(1);
        }
        // 检查端口号是否在有效范围内
        if (server_port < 0 || server_port > 65535) {
            System.err.println("端口号必须在 0 到 65535 之间。");
            System.exit(1);
        }
        if (Lmin <= 0) {
            System.err.println("每次传输的最小字节数应该大于0。");
            System.exit(1);
        }
        int file_size = (int) file.length();
        if(Lmax > file_size){
            System.err.println("每次传输的最大字节数大于文件大小，已经自动将最大传输最大字节数设置为文件的大小。");
            Lmax = file_size;
        }
        try {
            SocketChannel socket_channel = SocketChannel.open();
            socket_channel.connect(new InetSocketAddress(server_ip, server_port));

            System.out.println(file_size);
            List<Integer> blocks = generateRandomIntegers(file_size, Lmin, Lmax);
            int blocks_num = blocks.size();

            // 发送Initialization报文
            sendInitializationMessage(socket_channel, blocks_num);

            // 接收agree报文
            if (!receiveAgreeMessage(socket_channel)) {
                System.err.println("未收到服务器的同意报文.");
                socket_channel.close();
                System.exit(1);
            }

            // 读取文件并发送reverseRequest报文
            sendReverseRequestMessages(socket_channel, file_path, blocks, file_size);

            socket_channel.close();
        } catch (UnknownHostException e) {
            System.err.println("无法解析的地址或域名！");
        } catch (NumberFormatException e){
            System.err.println("端口号、每次传输的最小字节数和最大字节数都应该是整数！");
        } catch(IOException e) {
            System.out.println("连接中断");
        }
    }
    // 生成随机长度的块列表，确保总长度为文件大小
    private static List<Integer> generateRandomIntegers(Integer file_size, Integer Lmin, Integer Lmax) {
        Random random = new Random();
        int sum = 0;
        List<Integer> blocks = new ArrayList<>();
        while (sum < file_size) {
            //[Lmin, Lmax + 1)
            int random_number = random.nextInt(Lmax - Lmin + 1) + Lmin;
            if (sum + random_number > file_size) {
                random_number = file_size - sum;
            }
            blocks.add(random_number);
            sum += random_number;
        }
        return blocks;
    }
    // 发送Initialization报文
    private static void sendInitializationMessage(SocketChannel socket_channel, int num_blocks) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(6);
        buffer.putShort((short) 1); // Type
        buffer.putInt(num_blocks); // N
        buffer.flip();
        socket_channel.write(buffer);
    }

    // 接收agree报文
    private static boolean receiveAgreeMessage(SocketChannel socket_channel) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(2);
        socket_channel.read(buffer);
        buffer.flip();
        short type = buffer.getShort(); // Type
        return type == 2;
    }

    // 读取文件并发送reverseRequest报文
    private static void sendReverseRequestMessages(SocketChannel socket_channel, String file_path, List<Integer> blocks, int file_size) throws IOException {
        byte[] fileBytes = Files.readAllBytes(Paths.get(file_path));
        // 创建一个随机命名的输出文件
        String output_file_path = "output_" + System.currentTimeMillis() + ".txt";
        Path output_path = Paths.get(output_file_path);
        int end_index = file_size;
        int block_num = blocks.size();
        for (int i = block_num - 1; i >= 0; i--) {
            int blockSize = blocks.get(i);
            ByteBuffer buffer = ByteBuffer.allocate(10 + blockSize);
            buffer.putShort((short) 3); // Type
            buffer.putInt(blockSize); // Length
            buffer.putInt(block_num - i); // block_num
            buffer.put(fileBytes, end_index - blockSize, blockSize); // Data
            buffer.flip();
            socket_channel.write(buffer);
            end_index -= blockSize;
            readAndWriteReverseAnswerMessage(socket_channel, output_path);
        }
    }

    // 接收并写入reverseAnswer报文
    private static void readAndWriteReverseAnswerMessage(SocketChannel socketChannel, Path outputPath) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
        socketChannel.read(buffer);
        buffer.flip();
        short type = buffer.getShort(); // Type
        if (type == 4) {
            int length = buffer.getInt(); // Length
            int blockNumber = buffer.getInt();
            byte[] data = new byte[length];
            buffer.get(data);
            String reversedData = new String(data);
            System.out.println("第" + blockNumber + "块: " + reversedData);

            // 将反转的数据写入缓存或文件
            Files.write(outputPath, reversedData.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
    }
}
