package tsubota1991tech.github.io.aws_game_manager.service;

public interface GameServerService {

    /**
     * 指定されたゲームサーバ（EC2 インスタンス）を起動する
     */
    void startServer(Long gameServerId);

    /**
     * 指定されたゲームサーバ（EC2 インスタンス）を停止する
     */
    void stopServer(Long gameServerId);

    /**
     * 指定されたゲームサーバの現在ステータスを AWS から取得して更新する
     */
    void refreshStatus(Long gameServerId);
}
