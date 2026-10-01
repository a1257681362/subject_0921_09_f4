package com.evops.ratecard.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evops.ratecard.entity.QuoteLine;
import org.apache.ibatis.annotations.Mapper;

/** 报价单行 Mapper。 */
@Mapper
public interface QuoteLineMapper extends BaseMapper<QuoteLine> {
}
