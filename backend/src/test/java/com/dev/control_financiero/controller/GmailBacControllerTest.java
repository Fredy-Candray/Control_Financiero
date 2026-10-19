package com.dev.control_financiero.controller;

import com.dev.control_financiero.repository.*;
import com.dev.control_financiero.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.domain.PageRequest;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GmailBacControllerTest {
    final BankEmailImportRepository imports=mock(BankEmailImportRepository.class);
    final GmailBacController controller=new GmailBacController(mock(GmailBacClient.class),mock(GmailTokenCipher.class),
        mock(GmailConnectionRepository.class),imports,mock(GmailConnectionService.class),mock(GmailBacScheduler.class));
    MockHttpServletRequest request() {
        var r=new MockHttpServletRequest(); r.getSession().setAttribute("userId",7L); return r;
    }
    @Test void requiresSession() {
        assertThrows(ResponseStatusException.class,()->controller.notifications(null,new MockHttpServletRequest()));
        verifyNoInteractions(imports);
    }
    @Test void baselineDoesNotReplayHistory() {
        when(imports.notificationCursor(7L)).thenReturn(100L);
        var page=controller.notifications(null,request());
        assertEquals(100L,page.cursor()); assertTrue(page.movements().isEmpty());
        verify(imports).notificationCursor(7L); verifyNoMoreInteractions(imports);
    }
    @Test void scopesToSessionAndAdvancesCursor() {
        var notice=mock(BankEmailImportRepository.MovementNotice.class);
        when(notice.getId()).thenReturn(103L);
        when(imports.notifications(7L,100L,PageRequest.of(0,50))).thenReturn(List.of(notice));
        var page=controller.notifications(100L,request());
        assertEquals(103L,page.cursor()); assertEquals(List.of(notice),page.movements());
    }
    @Test void rejectsNegativeCursor() {
        assertThrows(ResponseStatusException.class,()->controller.notifications(-1L,request()));
        verifyNoInteractions(imports);
    }
}
