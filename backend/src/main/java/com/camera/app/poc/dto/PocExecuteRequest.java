package com.camera.app.poc.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PocExecuteRequest {

    /** 传给 POC 脚本的命令行参数，最多 20 个，每个不超过 1000 字符 */
    @Size(max = 20, message = "最多传入 20 个参数")
    private List<@Size(max = 1000, message = "单个参数不超过 1000 字符") String> arguments = new ArrayList<>();

    /** 执行超时（秒），默认 10 秒，最大 30 秒 */
    @Min(value = 1, message = "超时时间最小 1 秒")
    @Max(value = 30, message = "超时时间最大 30 秒")
    private int timeoutSeconds = 10;

    /**
     * 可选：关联资产 ID。
     * - 不传 assetPort：将资产 IP 作为第一个参数注入（适合位置参数型 POC）
     * - 同时传 assetPort：自动拼成 "-u http://ip:port" 注入（适合 argparse -u URL 型 POC）
     */
    private Long assetId;

    /**
     * 可选：与 assetId 配合使用的端口号（1-65535）。
     * 传入后注入形式变为：-u http://{assetIp}:{assetPort}
     */
    @Min(value = 1, message = "端口号最小 1")
    @Max(value = 65535, message = "端口号最大 65535")
    private Integer assetPort;
}
