package com.jworks.forge.gen.safety;

/**
 * 타겟 파일쓰기 경로가 안전 규칙을 위반했을 때 던진다.
 * (루트 밖 탈출, 절대경로/드라이브/UNC, 심볼릭링크 탈출, 비허용 확장자 등)
 */
public class PathSafetyException extends RuntimeException {

    public PathSafetyException(String message) {
        super(message);
    }
}
