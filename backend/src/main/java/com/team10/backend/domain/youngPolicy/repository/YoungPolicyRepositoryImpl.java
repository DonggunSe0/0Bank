package com.team10.backend.domain.youngPolicy.repository;

import static com.team10.backend.domain.youngPolicy.entity.QYoungPolicy.youngPolicy;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.team10.backend.domain.youngPolicy.type.Region;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.team10.backend.domain.youngPolicy.dto.req.YoungPolicySearchReq;
import com.team10.backend.domain.youngPolicy.entity.YoungPolicy;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.util.StringUtils;

@RequiredArgsConstructor
public class YoungPolicyRepositoryImpl implements YoungPolicyRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<YoungPolicy> search(YoungPolicySearchReq filter, Pageable pageable) {
        BooleanBuilder condition = buildSearchCondition(filter);

        List<YoungPolicy> content = queryFactory
                .selectFrom(youngPolicy)
                .where(condition)
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(youngPolicy.count())
                .from(youngPolicy)
                .where(condition)
                .fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0L : total);
    }

    private BooleanBuilder buildSearchCondition(YoungPolicySearchReq filter) {
        return new BooleanBuilder()
                .and(ageLoeAndGoe(filter.age()))
                .and(regionContains(filter.region()))
                .and(categoryEq(filter.category()))
                .and(keywordContains(filter.keyword()));
    }

    private BooleanBuilder ageLoeAndGoe(Integer age) {
        if (age == null) {
            return null;
        }
        BooleanBuilder builder = new BooleanBuilder();
        // 최소연령 조건: 최소연령 정보가 없거나, 0세이거나, 입력한 나이가 최소연령 이상인 경우
        builder.and(youngPolicy.minAge.isNull()
                .or(youngPolicy.minAge.eq(0))
                .or(youngPolicy.minAge.loe(age)));
        
        // 최대연령 조건: 최대연령 정보가 없거나, 0세이거나, 99세이거나, 입력한 나이가 최대연령 이하인 경우
        builder.and(youngPolicy.maxAge.isNull()
                .or(youngPolicy.maxAge.eq(0))
                .or(youngPolicy.maxAge.eq(99))
                .or(youngPolicy.maxAge.goe(age)));
        
        return builder;
    }

    private BooleanExpression regionContains(String regionName) {
        if (!StringUtils.hasText(regionName)) {
            return null;
        }

        // 1. 시도 코드로 변환 시도 (예: "서울 마포구" -> "11")
        String derivedCode = Region.findCodeByName(regionName);

        if (derivedCode != null) {
            return youngPolicy.regionCode.contains(derivedCode)
                    .or(youngPolicy.regionCode.contains("전국"))
                    .or(youngPolicy.regionCode.contains("3001"))
                    .or(youngPolicy.regionCode.contains("003002001"))
                    .or(youngPolicy.regionCode.isNull())
                    .or(youngPolicy.regionCode.isEmpty());
        }

        // 2. 만약 매핑된 코드가 없다면 한글 검색어 매칭 기회를 제공합니다. (예: region_code 컬럼에 한글로 직접 저장된 경우나 전국 검색 시)
        return youngPolicy.regionCode.containsIgnoreCase(regionName)
                .or(youngPolicy.regionCode.contains("전국"))
                .or(youngPolicy.regionCode.contains("3001"))
                .or(youngPolicy.regionCode.contains("003002001"))
                .or(youngPolicy.regionCode.isNull())
                .or(youngPolicy.regionCode.isEmpty());
    }

    private BooleanExpression categoryEq(String category) {
        if (!StringUtils.hasText(category)) {
            return null;
        }
        return youngPolicy.category.eq(category);
    }

    private BooleanExpression keywordContains(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        return youngPolicy.title.containsIgnoreCase(keyword)
                .or(youngPolicy.description.containsIgnoreCase(keyword));
    }
}
