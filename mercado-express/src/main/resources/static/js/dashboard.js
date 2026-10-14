// Mercado Express - gráficos do dashboard da gerência (Chart.js)
document.addEventListener("DOMContentLoaded", () => {
    const secao = document.querySelector("[data-dashboard]");
    if (!secao || typeof Chart === "undefined") return;

    const cores = {
        receita: "#1e8a5f",
        lucro: "#f28c28",
        azul: "#2463c4",
        cinza: "#d5ddd9",
        vermelho: "#c62f2f",
        laranja: "#f28c28"
    };
    const moeda = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });

    Chart.defaults.font.family = '"Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif';
    Chart.defaults.color = "#47524d";
    Chart.defaults.maintainAspectRatio = false;

    const tooltipMoeda = {
        callbacks: { label: (ctx) => `${ctx.dataset.label}: ${moeda.format(ctx.parsed.y ?? ctx.parsed)}` }
    };
    const eixoMoeda = { beginAtZero: true, ticks: { callback: (valor) => moeda.format(valor) } };
    const numero = (valor) => Number(valor ?? 0);

    fetch(secao.dataset.dashboard, { headers: { Accept: "application/json" } })
        .then((resposta) => {
            if (!resposta.ok) throw new Error(`HTTP ${resposta.status}`);
            return resposta.json();
        })
        .then((dados) => {
            // Vendas por dia: barras de receita + linha de lucro
            new Chart(document.getElementById("grafico-vendas-dia"), {
                type: "bar",
                data: {
                    labels: dados.vendasPorDia.map((d) => d.rotulo),
                    datasets: [
                        { label: "Receita", data: dados.vendasPorDia.map((d) => numero(d.receita)), backgroundColor: cores.receita, order: 2 },
                        { label: "Lucro", type: "line", data: dados.vendasPorDia.map((d) => numero(d.lucro)),
                          borderColor: cores.lucro, backgroundColor: cores.lucro, tension: 0.3, order: 1 }
                    ]
                },
                options: { plugins: { tooltip: tooltipMoeda }, scales: { y: eixoMoeda } }
            });

            // Receita x lucro por setor
            new Chart(document.getElementById("grafico-setores"), {
                type: "bar",
                data: {
                    labels: dados.vendasPorSetor.map((s) => s.rotulo),
                    datasets: [
                        { label: "Receita", data: dados.vendasPorSetor.map((s) => numero(s.receita)), backgroundColor: cores.receita },
                        { label: "Lucro", data: dados.vendasPorSetor.map((s) => numero(s.lucro)), backgroundColor: cores.lucro }
                    ]
                },
                options: { plugins: { tooltip: tooltipMoeda }, scales: { y: eixoMoeda } }
            });

            // Top 5 produtos (barras horizontais, em unidades)
            new Chart(document.getElementById("grafico-top-produtos"), {
                type: "bar",
                data: {
                    labels: dados.produtosMaisVendidos.map((p) => p.rotulo),
                    datasets: [{ label: "Unidades vendidas", data: dados.produtosMaisVendidos.map((p) => numero(p.quantidade)),
                                 backgroundColor: cores.azul }]
                },
                options: { indexAxis: "y", scales: { x: { beginAtZero: true, ticks: { precision: 0 } } } }
            });

            // Status dos pagamentos (rosca)
            const status = dados.pedidosPorStatus;
            new Chart(document.getElementById("grafico-status"), {
                type: "doughnut",
                data: {
                    labels: Object.keys(status),
                    datasets: [{ data: Object.values(status), backgroundColor: [cores.laranja, cores.receita, cores.vermelho] }]
                },
                options: { plugins: { legend: { position: "bottom" } } }
            });

            // Estoque atual por produto (laranja = estoque baixo)
            new Chart(document.getElementById("grafico-estoque"), {
                type: "bar",
                data: {
                    labels: dados.estoque.map((e) => e.produto),
                    datasets: [{ label: "Unidades em estoque", data: dados.estoque.map((e) => e.quantidade),
                                 backgroundColor: dados.estoque.map((e) => (e.baixo ? cores.laranja : cores.receita)) }]
                },
                options: { plugins: { legend: { display: false } }, scales: { y: { beginAtZero: true, ticks: { precision: 0 } } } }
            });
        })
        .catch((erro) => {
            secao.insertAdjacentHTML("afterbegin",
                '<p class="alerta alerta--erro">Não foi possível carregar os gráficos.</p>');
            console.error("Dashboard:", erro);
        });
});
