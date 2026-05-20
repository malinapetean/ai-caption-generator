package com.example.app.service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.example.app.dto.stats.StatsResponse;
import com.example.app.dto.stats.StyleStatDto;
import com.example.app.model.AppUser;
import com.example.app.model.Caption;
import com.example.app.repository.CaptionRepository;
import com.example.app.repository.ImageRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class StatsService {

    private static final String UNKNOWN_STYLE = "unknown";

    private final CaptionRepository captionRepository;
    private final ImageRecordRepository imageRecordRepository;

    public StatsService(
            CaptionRepository captionRepository,
            ImageRecordRepository imageRecordRepository
    ) {
        this.captionRepository = captionRepository;
        this.imageRecordRepository = imageRecordRepository;
    }

    @Transactional(readOnly = true)
    public StatsResponse getStats(AppUser user) {
        Long userId = user.getId();
        List<Caption> captions = captionRepository.findByImageUserIdOrderByCreatedAtDesc(userId);
        long totalCaptions = captions.size();
        long totalSelectedCaptions = captions.stream()
                .filter(Caption::isSelected)
                .count();
        long totalUploadedImages = imageRecordRepository.countByUserId(userId);

        Map<String, Long> generatedCounts = captions.stream()
                .collect(Collectors.groupingBy(
                        caption -> normalizeStyle(caption.getStyle()),
                        Collectors.counting()
                ));
        Map<String, Long> selectedCounts = captions.stream()
                .filter(Caption::isSelected)
                .collect(Collectors.groupingBy(
                        caption -> normalizeStyle(caption.getStyle()),
                        Collectors.counting()
                ));

        List<StyleStatDto> generatedByStyle = toStyleStats(generatedCounts, totalCaptions);
        List<StyleStatDto> selectedByStyle = toStyleStats(selectedCounts, totalSelectedCaptions);
        List<StatsResponse.SelectionRateStatDto> selectionRateByStyle = toSelectionRateStats(
                generatedCounts,
                selectedCounts
        );

        String mostUsedStyle = findTopStyle(generatedCounts);
        String mostSelectedStyle = findTopStyle(selectedCounts);
        String preferredStyle = totalSelectedCaptions > 0 ? mostSelectedStyle : mostUsedStyle;

        return new StatsResponse(
                totalCaptions,
                totalSelectedCaptions,
                totalUploadedImages,
                mostUsedStyle,
                mostSelectedStyle,
                preferredStyle,
                generatedByStyle,
                selectedByStyle,
                selectionRateByStyle
        );
    }

    private List<StyleStatDto> toStyleStats(Map<String, Long> counts, long total) {
        return orderCounts(counts).entrySet().stream()
                .map(entry -> new StyleStatDto(
                        entry.getKey(),
                        entry.getValue(),
                        calculatePercentage(entry.getValue(), total)
                ))
                .toList();
    }

    private List<StatsResponse.SelectionRateStatDto> toSelectionRateStats(
            Map<String, Long> generatedCounts,
            Map<String, Long> selectedCounts
    ) {
        return Stream.concat(generatedCounts.keySet().stream(), selectedCounts.keySet().stream())
                .distinct()
                .map(style -> {
                    long generatedCount = generatedCounts.getOrDefault(style, 0L);
                    long selectedCount = selectedCounts.getOrDefault(style, 0L);
                    return new StatsResponse.SelectionRateStatDto(
                            style,
                            generatedCount,
                            selectedCount,
                            calculatePercentage(selectedCount, generatedCount)
                    );
                })
                .sorted(Comparator
                        .comparingDouble(StatsResponse.SelectionRateStatDto::selectionRate)
                        .reversed()
                        .thenComparing(StatsResponse.SelectionRateStatDto::generatedCount, Comparator.reverseOrder())
                        .thenComparing(StatsResponse.SelectionRateStatDto::style))
                .toList();
    }

    private Map<String, Long> orderCounts(Map<String, Long> counts) {
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private String findTopStyle(Map<String, Long> counts) {
        return orderCounts(counts).keySet().stream().findFirst().orElse(null);
    }

    private String normalizeStyle(String style) {
        if (!StringUtils.hasText(style)) {
            return UNKNOWN_STYLE;
        }

        return style.trim().toLowerCase(Locale.ROOT);
    }

    private double calculatePercentage(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0.0;
        }

        return numerator * 100.0 / denominator;
    }
}
