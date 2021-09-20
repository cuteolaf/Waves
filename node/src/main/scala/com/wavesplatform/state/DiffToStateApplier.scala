package com.wavesplatform.state

import cats.syntax.monoid._
import com.wavesplatform.account.Address
import com.wavesplatform.transaction.Asset
import com.wavesplatform.transaction.Asset.Waves

/**
  * A set of functions that apply diff
  * to the blockchain and return new
  * state values (only changed ones)
  */
object DiffToStateApplier {
  case class PortfolioUpdates(
      balances: Map[Address, Map[Asset, Long]],
      leases: Map[Address, LeaseBalance]
  )

  def portfolios(blockchain: Blockchain, diff: Diff): PortfolioUpdates = {
    val balances = Map.newBuilder[Address, Map[Asset, Long]]
    val leases   = Map.newBuilder[Address, LeaseBalance]

    for ((address, portfolioDiff) <- diff.portfolios) {
      // balances for address
      val bs = Map.newBuilder[Asset, Long]

      if (portfolioDiff.balance != 0) {
        bs += Waves -> math.addExact(blockchain.balance(address, Waves), portfolioDiff.balance).ensuring(_ >= 0)
      }

      portfolioDiff.assets.collect {
        case (asset, delta) if delta != 0 =>
          bs += asset -> math.addExact(blockchain.balance(address, asset), delta).ensuring(_ >= 0)
      }

      balances += address -> bs.result()

      // leases
      if (portfolioDiff.lease != LeaseBalance.empty) {
        leases += address -> blockchain.leaseBalance(address).combine(portfolioDiff.lease)
      }
    }

    PortfolioUpdates(balances.result(), leases.result())
  }
}
