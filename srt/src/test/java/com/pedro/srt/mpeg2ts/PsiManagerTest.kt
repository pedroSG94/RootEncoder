package com.pedro.srt.mpeg2ts

import com.pedro.srt.mpeg2ts.psi.PsiManager
import com.pedro.srt.mpeg2ts.psi.TableToSend
import com.pedro.srt.mpeg2ts.service.Mpeg2TsService
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Created by pedro on 9/9/23.
 */
class PsiManagerTest {

  private val service = Mpeg2TsService()

  @Test
  fun `GIVEN a psiManager WHEN call should send is key false patPeriod times THEN return TableToSend PAT_PMT`() {
    val psiManager = PsiManager(service)
    var sendValue = TableToSend.NONE
    (0..PsiManager.patPeriod).forEach { _ ->
      sendValue = psiManager.shouldSend(false)
    }
    assertEquals(TableToSend.PAT_PMT, sendValue)
  }

  @Test
  fun `GIVEN a psiManager WHEN call should send is key false sdtPeriod times THEN return TableToSend ALL`() {
    val psiManager = PsiManager(service)
    var sendValue = TableToSend.NONE
    (0..PsiManager.sdtPeriod).forEach { _ ->
      sendValue = psiManager.shouldSend(false)
    }
    assertEquals(TableToSend.ALL, sendValue)
  }

  @Test
  fun `GIVEN a psiManager WHEN update pat and sdt version THEN get version 1`() {
    val psiManager = PsiManager(service)
    psiManager.upgradePatVersion()
    psiManager.upgradeSdtVersion()
    assertEquals(0x01.toByte(), psiManager.getPat().version)
    assertEquals(0x01.toByte(), psiManager.getSdt().version)
  }

  @Test
  fun `GIVEN a psiManager WHEN update update service THEN get get new service in pat and sdt`() {
    val psiManager = PsiManager(service)
    val name = "name updated"
    val serviceUpdated = service.copy(name = name)
    psiManager.updateService(serviceUpdated)
    assertEquals(name, psiManager.getPat().service.name)
    assertEquals(name, psiManager.getSdt().service.name)
  }
}