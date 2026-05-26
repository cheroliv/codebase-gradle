package codebase.scenarios

import vibecoding.contracts.state.AugmentedState
import codebase.koog.KoogAugmentedContextGraph
import java.io.File

/**
 * World Object injecté par PicoContainer dans toutes les Steps Cucumber
 * de type augmented_planning.
 *
 * Pattern aligné sur plantuml-gradle (PlantumlWorld) :
 * - Injection par constructeur dans les Steps
 * - État mutable partagé entre les scénarios
 * - PicoContainer crée une nouvelle instance par scénario → pas besoin de reset()
 * - Le graphe est initialisé paresseusement (appel LLM évité tant que `execute()` n'est pas appelé)
 *
 * L-3 : introduit pour les tests Cucumber du pipeline KoogAugmentedContextGraph.
 */
class AugmentedPlanningWorld {

    val workspaceRoot: File = File("/tmp/augmented-planning-test").also { it.mkdirs() }
    var intention: String = ""
    var resultState: AugmentedState? = null
    val graph: KoogAugmentedContextGraph by lazy { KoogAugmentedContextGraph() }
}
