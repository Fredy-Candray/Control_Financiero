package com.dev.control_financiero.service;

import com.dev.control_financiero.entity.Categoria;
import com.dev.control_financiero.repository.CategoriaRepository;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MerchantCategoryServiceTest {
    final CategoriaRepository repository=mock(CategoriaRepository.class);
    final MerchantCategoryService service=new MerchantCategoryService(repository);
    @Test void clasificaComerciosYNormalizaAcentosYMayusculas() {
        assertEquals("Comida",service.suggestedName("LACA LACA PLAZA UNIVER"));
        assertEquals("Comida",service.suggestedName("Cafetería Los Pinos"));
        assertEquals("Supermercado",service.suggestedName("MAXI DESPENSA SN MARCO"));
        assertEquals("Supermercado",service.suggestedName("SELECTOS ARCE"));
        assertEquals("Combustible",service.suggestedName("GASOLINERA CENTRAL"));
        assertEquals("Salud",service.suggestedName("FARMACIAS DEL CENTRO"));
    }
    @Test void noAdivinaComprasGenericasPersonasNiCoincidenciasParciales() {
        for(String merchant:List.of("CC12COMPRA EN CUOTA MA","PAGO A JUAN PEREZ","COMPRA 123","SHELLY TORRES","RESTAURANTERO",""))
            assertNull(service.categoryId(merchant));
        assertNull(service.categoryId(null));
        verifyNoInteractions(repository);
    }
    @Test void dejaSinCategoriaCuandoHayReglasContradictorias() {
        assertNull(service.categoryId("FARMACIA SUPER SELECTOS"));
        verifyNoInteractions(repository);
    }
    @Test void reutilizaCategoriaCompatibleExistente() {
        when(repository.findAll()).thenReturn(List.of(Categoria.builder().id(7L).nombre("Alimentación").activa(true).build()));
        assertEquals(7L,service.categoryId("LACA LACA"));
        verify(repository,never()).save(any());
    }
    @Test void creaCategoriaSoloSiReconoceUnComercioYSinDuplicarla() {
        var category=Categoria.builder().id(8L).nombre("Supermercado").activa(true).build();
        when(repository.findAll()).thenReturn(List.of(),List.of(category));
        when(repository.save(any())).thenReturn(category);
        assertEquals(8L,service.categoryId("MAXI DESPENSA"));
        assertEquals(8L,service.categoryId("SELECTOS ARCE"));
        verify(repository,times(1)).save(argThat(c->c.getNombre().equals("Supermercado")&&c.getActiva()));
    }
    @Test void respetaCategoriasDesactivadas() {
        when(repository.findAll()).thenReturn(List.of(Categoria.builder().id(7L).nombre("Alimentación").activa(false).build()));
        assertNull(service.categoryId("LACA LACA"));
        verify(repository,never()).save(any());
    }
}
