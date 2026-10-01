package com.evops.ratecard.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evops.ratecard.entity.RateCardTier;
import org.apache.ibatis.annotations.Mapper;

/** 版本周数阶梯折扣行 Mapper。 */
@Mapper
public interface RateCardTierMapper extends BaseMapper<RateCardTier> {
}
