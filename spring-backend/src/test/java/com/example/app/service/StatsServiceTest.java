package com.example.app.service;

import java.util.List;

import com.example.app.dto.stats.StatsResponse;
import com.example.app.model.AppUser;
import com.example.app.model.Caption;
import com.example.app.model.ImageRecord;
import com.example.app.repository.CaptionRepository;
import com.example.app.repository.ImageRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatsServiceTest {

    @Mock
    private CaptionRepository captionRepository;

    @Mock
    private ImageRecordRepository imageRecordRepository;

    @InjectMocks
    private StatsService statsService;

    @Test
    void getStats_normalizesStylesAndComputesAggregates() {
        AppUser user = new AppUser();
        user.setId(4L);

        ImageRecord imageRecord = new ImageRecord();
        imageRecord.setId(10L);
        imageRecord.setUser(user);

        Caption first = caption(imageRecord, " Casual ", false);
        Caption second = caption(imageRecord, "casual", true);
        Caption third = caption(imageRecord, "minimalist", true);
        Caption fourth = caption(imageRecord, " ", false);

        when(captionRepository.findByImageUserIdOrderByCreatedAtDesc(4L)).thenReturn(List.of(first, second, third, fourth));
        when(imageRecordRepository.countByUserId(4L)).thenReturn(2L);

        StatsResponse response = statsService.getStats(user);

        assertThat(response.totalCaptions()).isEqualTo(4);
        assertThat(response.totalSelectedCaptions()).isEqualTo(2);
        assertThat(response.totalUploadedImages()).isEqualTo(2);
        assertThat(response.mostUsedStyle()).isEqualTo("casual");
        assertThat(response.mostSelectedStyle()).isEqualTo("casual");
        assertThat(response.preferredStyle()).isEqualTo("casual");
        assertThat(response.generatedByStyle()).extracting(stat -> stat.style())
                .containsExactly("casual", "minimalist", "unknown");
        assertThat(response.selectedByStyle()).extracting(stat -> stat.style())
                .containsExactly("casual", "minimalist");
        assertThat(response.selectionRateByStyle()).extracting(StatsResponse.SelectionRateStatDto::style)
                .containsExactly("minimalist", "casual", "unknown");
    }

    @Test
    void getStats_withNoCaptions_returnsEmptyStats() {
        AppUser user = new AppUser();
        user.setId(4L);

        when(captionRepository.findByImageUserIdOrderByCreatedAtDesc(4L)).thenReturn(List.of());
        when(imageRecordRepository.countByUserId(4L)).thenReturn(0L);

        StatsResponse response = statsService.getStats(user);

        assertThat(response.totalCaptions()).isZero();
        assertThat(response.totalSelectedCaptions()).isZero();
        assertThat(response.totalUploadedImages()).isZero();
        assertThat(response.mostUsedStyle()).isNull();
        assertThat(response.mostSelectedStyle()).isNull();
        assertThat(response.preferredStyle()).isNull();
        assertThat(response.generatedByStyle()).isEmpty();
        assertThat(response.selectedByStyle()).isEmpty();
        assertThat(response.selectionRateByStyle()).isEmpty();
    }

    private Caption caption(ImageRecord imageRecord, String style, boolean selected) {
        Caption caption = new Caption();
        caption.setImage(imageRecord);
        caption.setStyle(style);
        caption.setSelected(selected);
        caption.setText("caption");
        return caption;
    }
}
