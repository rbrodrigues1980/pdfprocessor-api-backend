package br.com.verticelabs.pdfprocessor.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Matriz de autorização por ação do perfil EVALUATOR (deny-by-default).
 */
class EvaluatorAuthorizationFilterTest {

    private final EvaluatorAuthorizationFilter filter = new EvaluatorAuthorizationFilter();

    private boolean allowed(HttpMethod method, String path) throws Exception {
        Method m = EvaluatorAuthorizationFilter.class
                .getDeclaredMethod("isAllowedForEvaluator", HttpMethod.class, String.class);
        m.setAccessible(true);
        return (boolean) m.invoke(filter, method, path);
    }

    @Test
    void permiteLeituraUploadIrEExport() throws Exception {
        assertTrue(allowed(HttpMethod.GET, "/api/v1/persons"));
        assertTrue(allowed(HttpMethod.GET, "/api/v1/persons/abc123"));
        assertTrue(allowed(HttpMethod.GET, "/api/v1/persons/abc123/documents"));
        assertTrue(allowed(HttpMethod.GET, "/api/v1/persons/abc123/resumo-geral"));
        assertTrue(allowed(HttpMethod.GET, "/api/v1/persons/abc123/resumo-geral/pdf"));
        assertTrue(allowed(HttpMethod.GET, "/api/v1/persons/12345678900/excel"));
        assertTrue(allowed(HttpMethod.GET, "/api/v1/persons/abc123/excel-by-id"));
        assertTrue(allowed(HttpMethod.GET, "/api/v1/persons/12345678900/excel-by-tenant"));
        assertTrue(allowed(HttpMethod.GET, "/api/v1/persons/reports/clientes/excel"));
        assertTrue(allowed(HttpMethod.POST, "/api/v1/persons/abc123/income-tax/upload"));
        assertTrue(allowed(HttpMethod.POST, "/api/v1/persons/abc123/income-tax/bulk-upload"));
        assertTrue(allowed(HttpMethod.GET, "/api/v1/documents/doc123"));
        assertTrue(allowed(HttpMethod.GET, "/api/v1/documents/doc123/entries"));
    }

    @Test
    void bloqueiaCadastroContrachequeEExclusao() throws Exception {
        // Criar/editar/excluir/validar cliente
        assertFalse(allowed(HttpMethod.POST, "/api/v1/persons"));
        assertFalse(allowed(HttpMethod.PUT, "/api/v1/persons/abc123"));
        assertFalse(allowed(HttpMethod.DELETE, "/api/v1/persons/abc123"));
        assertFalse(allowed(HttpMethod.PATCH, "/api/v1/persons/abc123/activate"));
        assertFalse(allowed(HttpMethod.PATCH, "/api/v1/persons/abc123/deactivate"));
        assertFalse(allowed(HttpMethod.PATCH, "/api/v1/persons/abc123/validate"));
        // Contracheque
        assertFalse(allowed(HttpMethod.POST, "/api/v1/documents/upload"));
        assertFalse(allowed(HttpMethod.POST, "/api/v1/documents/bulk-upload"));
        assertFalse(allowed(HttpMethod.POST, "/api/v1/persons/abc123/documents/upload"));
        assertFalse(allowed(HttpMethod.POST, "/api/v1/persons/abc123/documents/bulk-upload"));
        // Excluir documento
        assertFalse(allowed(HttpMethod.DELETE, "/api/v1/persons/abc123/documents/doc123"));
        // Gestão de usuários/tenants/empresas/rubricas
        assertFalse(allowed(HttpMethod.GET, "/api/v1/users"));
        assertFalse(allowed(HttpMethod.POST, "/api/v1/users"));
        assertFalse(allowed(HttpMethod.GET, "/api/v1/tenants"));
        assertFalse(allowed(HttpMethod.POST, "/api/v1/empresas"));
        assertFalse(allowed(HttpMethod.POST, "/api/v1/rubricas"));
        // Dashboard não é acessível ao avaliador
        assertFalse(allowed(HttpMethod.GET, "/api/v1/dashboard"));
    }
}
