
# 📄 Módulo de Relatórios

Projeto Java para centralização e geração de relatórios PDF usando JasperReports.

Este módulo foi criado para evitar que cada microserviço precise ter suas próprias dependências de JasperReports, templates `.jrxml` ou lógica de geração de PDF.

## 🎯 Objetivo

✅ Centralizar todos os templates (.jrxml) em um único projeto.  
✅ Evitar duplicação de bibliotecas Jasper em cada microserviço.  
✅ Reduzir o tamanho das imagens Docker dos microserviços.  
✅ Facilitar manutenção, evoluções e correções futuras.  

---

## 🚀 Como usar

### 1. Adicione o `.jar` deste projeto como dependência em qualquer microserviço que precise gerar PDF.

Se você usa Maven:

```xml
		<dependency>
			<groupId>br.com.report</groupId>
			<artifactId>ReportProject</artifactId>
			<version>1.0.0</version>
		</dependency>
```
Ou, se ainda não publicou em repositório Maven, adicione manualmente o `.jar` no classpath.

---

### 2. Estrutura interna dos relatórios

- Cada relatório possui sua própria classe Java específica (Exemplo: `RelatorioEstadia`, `PessoasReport`).
- Todos os templates `.jrxml` ficam dentro de:  

```
src/main/resources/templates/
```

Exemplo de estrutura:

```
templates/
├── estadia.jrxml
└── pessoas.jrxml
```

---

### 3. Exemplo de uso no microserviço consumidor:

```java
import br.com.seuprojeto.relatorios.RelatorioEstadia;

public class RelatorioService {

    public byte[] gerarEstadia(EstadiaDto dados) {
        try {
            return new RelatorioEstadia().gerarPdf(dados);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao gerar o PDF de estadia", e);
        }
    }
}
```

---

### 4. Dependências usadas no projeto

- **JasperReports** (exemplo: versão `6.x`)
- **Spring Core** (opcional, caso queira usar algumas injeções ou utilitários)
- **Outras bibliotecas:** apenas as essenciais para Jasper e manipulação de PDF.

---

### 5. Como adicionar novos relatórios

1. Criar um novo `.jrxml` dentro da pasta `/templates`.
2. Criar uma nova classe Java seguindo o padrão das existentes (`RelatorioXyz.java`).
3. Criar um método que receba os dados de entrada e retorne o `byte[]` com o PDF.

---

### 6. Como exportar um PDF de forma genérica

Se quiser, use o método genérico da classe `PdfReportUtil`:

```java
byte[] pdf = PdfReportUtil.exportPdf("templates/nome_do_template.jrxml", parametros, dataSource);
```

---

### ✅ Boas práticas

- Não coloque lógica de negócio aqui. Apenas lógica de formatação e geração de PDF.
- Evite criar métodos que dependam de Spring ou de configuração de banco de dados.
- Sempre teste o novo relatório localmente antes de publicar a nova versão da biblioteca.

---

### 📌 Autor

Projeto interno da equipe de desenvolvimento da [9i].

---
