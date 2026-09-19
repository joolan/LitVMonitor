package com.litv.monitor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.litv.monitor.entity.UserMemo;
import com.litv.monitor.mapper.UserMemoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserMemoService {

    private final UserMemoMapper userMemoMapper;

    public UserMemo getMemo(String username) {
        return userMemoMapper.selectOne(
                new LambdaQueryWrapper<UserMemo>().eq(UserMemo::getUsername, username).last("LIMIT 1")
        );
    }

    public UserMemo saveMemo(String username, String content) {
        UserMemo memo = getMemo(username);
        if (memo == null) {
            memo = new UserMemo();
            memo.setUsername(username);
            memo.setContent(content);
            userMemoMapper.insert(memo);
        } else {
            memo.setContent(content);
            userMemoMapper.updateById(memo);
        }
        return memo;
    }
}
