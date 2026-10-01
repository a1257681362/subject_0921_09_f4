package com.evops.ratecard.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evops.ratecard.entity.Quote;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/** 报价单头 Mapper。 */
@Mapper
public interface QuoteMapper extends BaseMapper<Quote> {

    /** 取下一个报价单序列值（H2 序列保证并发取号不重）。 */
    @Select("SELECT NEXT VALUE FOR seq_quote_no")
    long nextQuoteSeq();
}
