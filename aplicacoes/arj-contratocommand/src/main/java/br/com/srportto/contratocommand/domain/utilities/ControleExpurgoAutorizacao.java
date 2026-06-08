package br.com.srportto.contratocommand.domain.utilities;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import br.com.srportto.contratocommand.shared.exceptions.BusinessException;

public class ControleExpurgoAutorizacao {

  public static int obterParticaoExpurgoWrite(LocalDate dataFinalizacao) {
        // O ChronoUnit.WEEKS calcula o número exato de semanas totais 
        // entre o Epoch (01/01/1970) e a data finalização
        long semanasTotais = ChronoUnit.WEEKS.between(LocalDate.ofEpochDay(0), dataFinalizacao);
        
        // Encontra a "gaveta" de 0 a 99
        int gaveta = (int) (semanasTotais % 100);
        
        // Soma 900 para cair na partição correta (900 a 999)
        return 900 + gaveta;
    }

    public static int obterParticaoExpurgoDrop(LocalDate dataReferenciaCalculoParticaoExpurgo) {
    
        // Descobre a particao de expurgo que a aplicação esta escrevendo no momento atual
        LocalDate dataAtual = LocalDate.now();
        var particaoExpurgoWriteMoment = obterParticaoExpurgoWrite(dataAtual);       

        if (dataReferenciaCalculoParticaoExpurgo.isBefore(dataAtual)) {
            throw new BusinessException("Data de referencia para expurgo invalida(no passado), pode pedir pra dropar a particao em escrita no momento " + dataReferenciaCalculoParticaoExpurgo);
        }

        // Considerando que a próxima partição de expurgo segura para delete é a que está 2 gavetas à frente da atual (ou seja, 2 semanas à frente da data atual)
        var particaoExpurgoDelete = (obterParticaoExpurgoWrite(dataReferenciaCalculoParticaoExpurgo)+ 2);

        if(particaoExpurgoDelete > 999) {
            particaoExpurgoDelete = particaoExpurgoDelete - 100; // Volta para o início do ciclo (900)
        }

        if(particaoExpurgoDelete == particaoExpurgoWriteMoment) {
            throw new BusinessException("A particao de expurgo selecionada para delete e a mesma que a particao de escrita atual, o que pode causar perda de dados. Data de referencia: " + dataReferenciaCalculoParticaoExpurgo);
        }

        //calcula diferenca para a próxima partição de expurgo
        //int particaoExpurgoMaxima = 999;
        //int diferencaParaProximaParticao = (particaoExpurgoMaxima - particaoExpurgoWriteMoment + 1) % 100;

        return particaoExpurgoDelete;

    }

}
