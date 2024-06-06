import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class NonBlockingClientGUI {
    private static JTextField server_ip_field;
    private static JTextField server_port_field;
    private static JTextField lmin_field;
    private static JTextField lmax_field;
    private static JList<File> file_list;
    private static DefaultListModel<File> list_model;
    private static JTextArea log_area;
    private static final int HEADERS_LENGTH = 10;
    private static final int BUFFER_LENGTH = 1024;

    public static void main(String[] args) {
        // 创建主窗口
        JFrame frame = new JFrame("Non-Blocking Client");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600); // 增加窗口大小
        frame.setResizable(false); // 固定窗口大小

        // 创建面板
        JPanel panel = new JPanel();
        frame.add(panel);
        placeComponents(panel);

        // 显示窗口
        frame.setVisible(true);
    }

    private static void placeComponents(JPanel panel) {
        panel.setLayout(null);

        // 服务器 IP 地址标签和文本框
        JLabel server_ip_label = new JLabel("Server IP:");
        server_ip_label.setBounds(10, 20, 80, 25);
        panel.add(server_ip_label);

        server_ip_field = new JTextField(20);
        server_ip_field.setBounds(100, 20, 160, 25);
        panel.add(server_ip_field);

        // 服务器端口标签和文本框
        JLabel server_port_label = new JLabel("Server Port:");
        server_port_label.setBounds(10, 50, 80, 25);
        panel.add(server_port_label);

        server_port_field = new JTextField(20);
        server_port_field.setBounds(100, 50, 160, 25);
        panel.add(server_port_field);

        // Lmin 标签和文本框
        JLabel lmin_label = new JLabel("Lmin:");
        lmin_label.setBounds(10, 80, 80, 25);
        panel.add(lmin_label);

        lmin_field = new JTextField(20);
        lmin_field.setBounds(100, 80, 160, 25);
        panel.add(lmin_field);

        // Lmax 标签和文本框
        JLabel lmax_label = new JLabel("Lmax:");
        lmax_label.setBounds(10, 110, 80, 25);
        panel.add(lmax_label);

        lmax_field = new JTextField(20);
        lmax_field.setBounds(100, 110, 160, 25);
        panel.add(lmax_field);

        // 文件列表标签和文件选择按钮
        JLabel file_list_label = new JLabel("Files:");
        file_list_label.setBounds(10, 140, 80, 25);
        panel.add(file_list_label);

        list_model = new DefaultListModel<>();
        file_list = new JList<>(list_model);
        JScrollPane list_scroll_pane = new JScrollPane(file_list);
        list_scroll_pane.setBounds(100, 140, 480, 150);
        panel.add(list_scroll_pane);

        // 选择文件按钮
        JButton choose_files_button = new JButton("Choose Files");
        choose_files_button.setBounds(600, 140, 125, 25);  // 创建一个按钮，用于选择文件
        choose_files_button.addActionListener(e -> {  // 为按钮添加动作监听器
            JFileChooser file_chooser = new JFileChooser(); // 创建一个文件选择器
            file_chooser.setMultiSelectionEnabled(true);    // 允许选择多个文件
            // 设置文件选择器的当前目录为用户目录
            //System.getProperty("user.dir")获取当前用户的目录路径
            file_chooser.setCurrentDirectory(new File(System.getProperty("user.dir")));
            int return_value = file_chooser.showOpenDialog(null); // 显示文件选择对话框
            if (return_value == JFileChooser.APPROVE_OPTION) { // 如果用户选择了文件
                File[] selected_files = file_chooser.getSelectedFiles(); // 获取用户选择的文件
                for (File file : selected_files) { // 遍历用户选择的文件
                    list_model.addElement(file); // 将文件添加到文件列表模型中
                }
            }
        });
        panel.add(choose_files_button);

        // 删除选中文件按钮
        JButton delete_files_button = new JButton("Delete Selected");
        delete_files_button.setBounds(600, 170, 125, 25);
        delete_files_button.addActionListener(e -> { // 为按钮添加动作监听器
            List<File> selected_files = file_list.getSelectedValuesList(); // 获取文件列表中选中的文件
            for (File file : selected_files) { // 遍历选中的文件
                list_model.removeElement(file); // 从文件列表模型中移除文件
            }
        });
        panel.add(delete_files_button);

        // 日志区域
        log_area = new JTextArea();
        log_area.setEditable(false);
        JScrollPane log_scroll_pane = new JScrollPane(log_area);
        log_scroll_pane.setBounds(10, 300, 760, 210);
        panel.add(log_scroll_pane);

        // 启动按钮
        JButton start_button = new JButton("Start");
        start_button.setBounds(350, 520, 80, 25);
        start_button.addActionListener(e -> startClient());
        panel.add(start_button);

    }

    private static void startClient() {
        try {
            String server_ip = server_ip_field.getText();
            int server_port = Integer.parseInt(server_port_field.getText());
            int lmin = Integer.parseInt(lmin_field.getText());
            int lmax = Integer.parseInt(lmax_field.getText());

            // 检查端口号是否合法
            if (server_port < 0 || server_port > 65535) {
                log_area.append("端口号必须在 0 到 65535 之间。\n");
                log_area.setCaretPosition(log_area.getDocument().getLength());
                return;
            }
            if(lmax < lmin){
                log_area.append("填入的每次传输的最大字节数小于最小字节数\n");
                log_area.setCaretPosition(log_area.getDocument().getLength());
                return;
            }
            // 检查 Lmin 是否大于 0
            if (lmin <= 0) {
                log_area.append("每次传输的最小字节数应该大于 0。\n");
                log_area.setCaretPosition(log_area.getDocument().getLength());
                return;
            }

            List<File> files = new ArrayList<>();
            for (int i = 0; i < list_model.getSize(); i++) {
                files.add(list_model.getElementAt(i));
            }

            if (files.isEmpty()) {
                log_area.append("请选择至少一个文件进行传输。\n");
                log_area.setCaretPosition(log_area.getDocument().getLength());
                return;
            }

            SocketChannel socket_channel = SocketChannel.open(); // 创建一个SocketChannel，用于与服务器通信
            socket_channel.connect(new InetSocketAddress(server_ip, server_port)); // 连接到服务器

            for (File file : files) { // 遍历文件列表中的文件
                log_area.append(file.getName() + " 开始传输。\n"); // 打印文件开始传输的信息
                int file_size = (int) file.length(); // 获取文件的大小

                // 如果 Lmax 大于文件大小，调整 Lmax 为文件大小
                if (lmax > file_size) {
                    log_area.append("Lmax 大于文件 " + file.getName() + " 的大小，将 Lmax 设置为文件大小。\n");
                    log_area.setCaretPosition(log_area.getDocument().getLength());
                    lmax = file_size;
                }

                List<Integer> blocks = generateRandomIntegers(file_size, lmin, lmax); // 生成一个随机整数列表，用于文件块的大小
                int blocks_num = blocks.size(); // 获取随机整数列表中的元素数量

                sendInitializationMessage(socket_channel, blocks_num); // 向服务器发送初始化消息，包括要传输的文件块数

                if (!receiveAgreeMessage(socket_channel)) { // 接收来自服务器的同意消息
                    log_area.append("未收到来自服务器的文件 " + file.getName() + " 的同意消息");
                    log_area.setCaretPosition(log_area.getDocument().getLength());
                    socket_channel.close(); // 如果未收到同意消息，关闭SocketChannel
                    return;
                }

                sendReverseRequestMessages(socket_channel, file, blocks, file_size); // 向服务器发送反转请求消息
                log_area.append(file.getName() + " 文件传输完成。\n");
            }

            socket_channel.close(); // 关闭SocketChannel
            log_area.append("所有文件传输完成。\n");
            log_area.setCaretPosition(log_area.getDocument().getLength()); // 将光标移动到日志区域的末尾
        } catch (IOException e) {
            log_area.append("Connection error: " + e.getMessage() + "\n");
            log_area.setCaretPosition(log_area.getDocument().getLength());
        } catch (NumberFormatException e) {
            log_area.append("输入无效！\n");
            log_area.setCaretPosition(log_area.getDocument().getLength());
        }
    }

    // 生成随机整数块列表，用于分割文件
    private static List<Integer> generateRandomIntegers(Integer file_size, Integer Lmin, Integer Lmax) {
        Random random = new Random();
        int sum = 0; // 当前已分割的文件总大小
        List<Integer> blocks = new ArrayList<>(); // 用于存储每块的大小
        while (sum < file_size) {
            // 在 [Lmin, Lmax + 1) 范围内生成随机整数
            int random_number = random.nextInt(Lmax - Lmin + 1) + Lmin;
            // 如果随机数大于缓冲区长度减去头部长度，则将随机数调整为缓冲区长度减去头部长度
            if (random_number > BUFFER_LENGTH - HEADERS_LENGTH) {
                random_number = BUFFER_LENGTH - HEADERS_LENGTH;
            }
            // 如果剩余的文件大小小于随机数，则调整随机数为剩余文件大小
            if (sum + random_number > file_size) {
                random_number = file_size - sum;
            }
            blocks.add(random_number); // 添加该块大小到块列表
            sum += random_number; // 更新已分割的文件总大小
        }
        return blocks; // 返回块列表
    }

    // 发送初始化消息，告知服务器块的数量
    private static void sendInitializationMessage(SocketChannel socket_channel, int num_blocks) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(6); // 分配6字节的缓冲区
        buffer.putShort((short) 1); // 消息类型，类型1表示初始化消息
        buffer.putInt(num_blocks); // 块的数量
        buffer.flip(); // 切换缓冲区为读模式
        socket_channel.write(buffer); // 发送缓冲区数据
    }

    // 接收服务器的同意消息
    private static boolean receiveAgreeMessage(SocketChannel socket_channel) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(2); // 分配2字节的缓冲区
        socket_channel.read(buffer); // 读取服务器响应
        buffer.flip(); // 切换缓冲区为读模式
        short type = buffer.getShort(); // 读取消息类型
        return type == 2; // 返回消息类型是否为2（表示同意）
    }

    // 发送文件数据块并接收服务器的反转数据
    private static void sendReverseRequestMessages(SocketChannel socket_channel, File file, List<Integer> blocks, int file_size) throws IOException {
        byte[] file_bytes = Files.readAllBytes(file.toPath()); // 读取文件的字节内容
        String output_file_path = "reverse_" + file.getName() + "output_" + System.currentTimeMillis() + ".txt";
        Path output_path = Paths.get(output_file_path); // 创建输出文件路径
        int end_index = file_size; // 文件末尾索引
        int block_num = blocks.size(); // 块的数量
        for (int i = block_num - 1; i >= 0; i--) {
            int block_size = blocks.get(i); // 获取当前块的大小
            ByteBuffer buffer = ByteBuffer.allocate(10 + block_size); // 分配缓冲区大小为10字节头部加上块大小
            buffer.putShort((short) 3); // 消息类型，类型3表示文件块数据
            buffer.putInt(block_size); // 块大小
            buffer.putInt(block_num - i); // 当前块编号
            buffer.put(file_bytes, end_index - block_size, block_size); // 添加文件块数据
            buffer.flip(); // 切换缓冲区为读模式

            socket_channel.write(buffer); // 发送缓冲区数据
            end_index -= block_size; // 更新文件末尾索引
            readAndWriteReverseAnswerMessage(socket_channel, output_path); // 接收并写入反转数据
        }
    }

    // 读取服务器返回的反转数据并写入文件
    private static void readAndWriteReverseAnswerMessage(SocketChannel socket_channel, Path output_path) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(BUFFER_LENGTH); // 分配缓冲区
        socket_channel.read(buffer); // 读取服务器响应
        buffer.flip(); // 切换缓冲区为读模式
        short type = buffer.getShort(); // 读取消息类型
        if (type == 4) { // 如果消息类型为4（表示反转数据）
            int length = buffer.getInt(); // 读取数据长度
            int block_number = buffer.getInt(); // 读取块编号
            byte[] data = new byte[length]; // 分配数据缓冲区
            buffer.get(data); // 读取数据
            String reversed_data = new String(data); // 将数据转换为字符串
            log_area.append("Block " + block_number + ": " + reversed_data + "\n"); // 在日志区域显示块编号和反转数据
            Files.write(output_path, reversed_data.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.APPEND); // 将反转数据写入文件
        }
    }

}
