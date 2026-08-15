package br.com.srportto.contratocommand.domain.utilities;

import br.com.srportto.contratocommand.shared.exceptions.BusinessException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class ControleExpurgoAutorizacao {

  public static int obterParticaoExpurgoWrite(LocalDate dataFinalizacao) {
        // Semanas desde o Epoch, na "gaveta" 0-99, deslocada para o range 900-999.
        long semanasTotais = ChronoUnit.WEEKS.between(LocalDate.ofEpochDay(0), dataFinalizacao);
        int gaveta = (int) (semanasTotais % 100);
        return 900 + gaveta;
    }

    public static int obterParticaoExpurgoDrop(LocalDate dataReferenciaCalculoParticaoExpurgo) {

        LocalDate dataAtual = LocalDate.now();
        var particaoExpurgoWriteMoment = obterParticaoExpurgoWrite(dataAtual);

        if (dataReferenciaCalculoParticaoExpurgo.isBefore(dataAtual)) {
            throw new BusinessException("Data de referencia para expurgo invalida(no passado), pode pedir pra dropar a particao em escrita no momento " + dataReferenciaCalculoParticaoExpurgo);
        }

        // Partição segura para delete: 2 gavetas (2 semanas) à frente da atual, margem de segurança.
        var particaoExpurgoDelete = (obterParticaoExpurgoWrite(dataReferenciaCalculoParticaoExpurgo)+ 2);

        if(particaoExpurgoDelete > 999) {
            particaoExpurgoDelete = particaoExpurgoDelete - 100; // volta ao início do ciclo
        }

        if(particaoExpurgoDelete == particaoExpurgoWriteMoment) {
            throw new BusinessException("A particao de expurgo selecionada para delete e a mesma que a particao de escrita atual, o que pode causar perda de dados. Data de referencia: " + dataReferenciaCalculoParticaoExpurgo);
        }

        return particaoExpurgoDelete;
    }

}
