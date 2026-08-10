package com.jworks.forge.code.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jworks.forge.code.domain.CommonCode;
import com.jworks.forge.code.mapper.CommonCodeMapper;

/**
 * 공통코드 조회 API (P1-4). 화면 셀렉트가 GRP_CODE로 호출.
 */
@RestController
@RequestMapping("/api/common-codes")
public class CommonCodeController {

    private final CommonCodeMapper mapper;

    public CommonCodeController(CommonCodeMapper mapper) {
        this.mapper = mapper;
    }

    @GetMapping("/{grpCode}")
    public List<CommonCode> byGroup(@PathVariable String grpCode) {
        return mapper.selectByGroup(grpCode);
    }
}
