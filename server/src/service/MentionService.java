package service;

import domain.User;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 채팅 문구에서 현재 방에 입장한 회원의 @닉네임 또는 @로그인ID를 찾는다.
 * 클라이언트가 보낸 "멘션 대상 ID"는 신뢰하지 않고 서버의 사용자 목록으로 판정한다.
 */
public class MentionService {

    public Set<Integer> findTargets(String text, List<User> roomUsers) {
        if (text == null || text.indexOf('@') < 0
                || roomUsers == null || roomUsers.isEmpty()) {
            return Set.of();
        }

        Map<String, Integer> handles = new LinkedHashMap<>();
        for (User user : roomUsers) {
            addHandle(handles, user.getName(), user.getMemberId());
            addHandle(handles, user.getId(), user.getMemberId());
        }

        // 겹치는 이름(@민, @민수)에서는 긴 이름을 먼저 검사한다.
        List<Map.Entry<String, Integer>> candidates =
                new ArrayList<>(handles.entrySet());
        candidates.sort(Comparator.comparingInt(
                (Map.Entry<String, Integer> entry) ->
                        entry.getKey().length()).reversed());

        Set<Integer> targets = new LinkedHashSet<>();
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) != '@'
                    || (i > 0 && isNameCharacter(text.charAt(i - 1)))) {
                continue;
            }

            int start = i + 1;
            for (Map.Entry<String, Integer> candidate : candidates) {
                String handle = candidate.getKey();
                int end = start + handle.length();
                if (end > text.length()
                        || !text.regionMatches(start, handle, 0, handle.length())
                        || (end < text.length()
                            && isNameCharacter(text.charAt(end)))) {
                    continue;
                }

                // 서로 다른 회원의 닉네임과 ID가 같은 경우에는 추측하지 않는다.
                if (candidate.getValue() != null) {
                    targets.add(candidate.getValue());
                }
                i = end - 1;
                break;
            }
        }
        return targets;
    }

    private static void addHandle(
            Map<String, Integer> handles, String handle, int userId) {
        if (handle == null || handle.isBlank() || userId <= 0) {
            return;
        }
        if (!handles.containsKey(handle)) {
            handles.put(handle, userId);
        } else if (!Integer.valueOf(userId).equals(handles.get(handle))) {
            handles.put(handle, null);
        }
    }

    private static boolean isNameCharacter(char character) {
        return Character.isLetterOrDigit(character) || character == '_';
    }
}
