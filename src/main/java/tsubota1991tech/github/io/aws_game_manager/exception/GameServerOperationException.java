package tsubota1991tech.github.io.aws_game_manager.exception;

/**
 * ゲームサーバ操作（起動/停止/状態確認）で
 * ユーザに伝えたいエラーが発生したときに投げる例外。
 */
public class GameServerOperationException extends RuntimeException {

    public GameServerOperationException(String message) {
        super(message);
    }

    public GameServerOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
