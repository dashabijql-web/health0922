package com.xzkj.hv2ops;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 运行中提示输入密码。有控制台时不回显；没有控制台（输入来自管道，比如自动化验收）时从标准输入读一行。
 * 密码不经过命令行参数，所以不会出现在进程列表、命令历史里。
 */
public interface Prompt {

    String secret(String label);

    static Prompt system() {
        return new Prompt() {
            private BufferedReader stdin;

            @Override
            public String secret(String label) {
                Console console = System.console();
                if (console != null) {
                    char[] chars = console.readPassword("%s", label);
                    if (chars == null) {
                        throw new OpsException("没有读到输入");
                    }
                    return new String(chars);
                }
                System.err.print(label);
                try {
                    if (stdin == null) {
                        stdin = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
                    }
                    String line = stdin.readLine();
                    System.err.println();
                    if (line == null) {
                        throw new OpsException("没有读到输入");
                    }
                    return line;
                } catch (IOException e) {
                    throw new OpsException("读取输入失败");
                }
            }
        };
    }
}
