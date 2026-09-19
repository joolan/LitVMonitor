package com.litv.monitor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.litv.monitor.dto.GlobalVariableDTO;
import com.litv.monitor.entity.GlobalVariable;
import com.litv.monitor.mapper.GlobalVariableMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GlobalVariableService {

    private final GlobalVariableMapper globalVariableMapper;

    public Page<GlobalVariable> listVariables(Page<GlobalVariable> page, String keyword) {
        LambdaQueryWrapper<GlobalVariable> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(GlobalVariable::getName, keyword);
        }
        wrapper.orderByDesc(GlobalVariable::getCreatedAt);
        Page<GlobalVariable> result = globalVariableMapper.selectPage(page, wrapper);
        result.getRecords().forEach(this::maskSecretValue);
        return result;
    }

    public GlobalVariable getVariableById(Long id) {
        GlobalVariable variable = globalVariableMapper.selectById(id);
        if (variable != null) maskSecretValue(variable);
        return variable;
    }

    public GlobalVariable createVariable(GlobalVariableDTO dto) {
        GlobalVariable variable = new GlobalVariable();
        BeanUtils.copyProperties(dto, variable);
        globalVariableMapper.insert(variable);
        return variable;
    }

    public GlobalVariable updateVariable(Long id, GlobalVariableDTO dto) {
        GlobalVariable variable = globalVariableMapper.selectById(id);
        if (variable == null) {
            return null;
        }

        variable.setName(dto.getName());
        variable.setDescription(dto.getDescription());
        variable.setIsSecret(dto.getIsSecret());

        // Skip value update if secret and value is masked (contains ****)
        // This prevents overwriting the real value with the masked display string
        if (dto.getValue() != null
                && !(Boolean.TRUE.equals(variable.getIsSecret()) && dto.getValue().contains("****"))) {
            variable.setValue(dto.getValue());
        }

        variable.setId(id);
        globalVariableMapper.updateById(variable);
        return variable;
    }

    public boolean deleteVariable(Long id) {
        return globalVariableMapper.deleteById(id) > 0;
    }

    public java.util.List<GlobalVariable> listAllVariables() {
        java.util.List<GlobalVariable> list = globalVariableMapper.selectList(
                new LambdaQueryWrapper<GlobalVariable>().last("LIMIT 1000"));
        list.forEach(this::maskSecretValue);
        return list;
    }

    private void maskSecretValue(GlobalVariable variable) {
        if (variable != null && Boolean.TRUE.equals(variable.getIsSecret()) && variable.getValue() != null) {
            // 完全脱敏，不保留任何字符
            variable.setValue(com.litv.monitor.util.SecretMasker.SENTINEL);
        }
    }
}
