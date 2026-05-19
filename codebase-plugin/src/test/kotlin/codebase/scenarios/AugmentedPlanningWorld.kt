package codebase.scenarios

import codebase.koog.AugmentedState
import codebase.koog.KoogAugmentedContextGraph
import java.io.File

/**
 * World Object injecté par PicoContainer dans toutes les Steps Cucumber
 * de type augmented_planning.
 *
 * Pattern aligné sur plantuml-gradle (PlantumlWorld) :
 * - Injection par constructeur dans les Steps
 * - État mutable partagé entre les scénarios (réinitialisé dans @Before)
 * - Pas de singleton global — propre à chaque scénario
 *
 * L-3 : introduit pour les tests Cucumber du pipeline KoogAugmentedContextGraph.
 */
class AugmentedPlanningWorld {

    var workspaceRoot: File = File("/tmp/augmented-planning-test")
    var intention = ""
    var resultState: AugmentedState? = null
    var graph: KoogAugmentedContextGraph? = null

    /**
     * Réinitialise le World entre chaque scénario.
     * Appelé par une Step @Before dans AugmentedPlanningSteps.
     */
    fun reset() {
        workspaceRoot = File("/tmp/augmented-planning-test")
        intention = ""
        resultState = null
        graph = null
    }
}
