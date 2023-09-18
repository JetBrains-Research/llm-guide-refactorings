package com.intellij.ml.llm.template.prompts

import com.intellij.ml.llm.template.models.openai.OpenAiChatMessage


fun fewShotExtractSuggestion(methodCode: String) = mutableListOf(
    OpenAiChatMessage(
        "system",
        """
                You are a skilled software developer. You have immense knowledge on software refactoring. 
                You communicate with a remote server that sends you code of functions (one function in a message) that it wants to simplify by applying extract method refactoring. 
                In return, you send a JSON object with suggestions of helpful extract method refactorings. It is important for suggestions to not contain the entire function body.
                Each suggestion consists of the start line, end line, and name for the extracted function.
                The JSON should have the following format: [{"function_name": <new function name>, "line_start": <line start>, "line_end": <line end>}, ..., ].
                """.trimIndent()
    ),
    OpenAiChatMessage(
        "user",
        """
                1. fun floydWarshall(graph: Array<IntArray>): Array<IntArray> {
                2.    val n = graph.size
                3.  val dist = Array(n) { i -> graph[i].clone() }
                4.
                5.    for (k in 0 until n) {
                6.     for (i in 0 until n) {
                7.           for (j in 0 until n) {
                8.               if (dist[i][k] != Int.MAX_VALUE && dist[k][j] != Int.MAX_VALUE
                9.                && dist[i][k] + dist[k][j] < dist[i][j]
                10.             ) {
                11.               dist[i][j] = dist[i][k] + dist[k][j]
                12.            }
                13.       }
                14.    }
                15. }
                16.
                17.  return dist
                18.  }
                """.trimIndent()
    ),
    OpenAiChatMessage(
        "assistant",
        """
                [
                {"function_name":  "floydWarshallUpdate", "line_start":  5, "line_end": 15}
                ]
                """.trimIndent()
    ),
    OpenAiChatMessage(
        "user",
        methodCode
    )
)


fun multishotExtractFunctionPrompt(codeSnippet: String) = mutableListOf(
    OpenAiChatMessage(
        "system",
        """
                You are a skilled software developer. You have immense knowledge on software refactoring. 
                You communicate with a remote server that sends you code of functions (one function in a message) that it wants to simplify by applying extract method refactoring. 
                In return, you send a JSON object with suggestions of helpful extract method refactorings. It is important for suggestions to not contain the entire function body.
                Each suggestion consists of the start line, end line, and name for the extracted function.
                The JSON should have the following format: [{"function_name": <new function name>, "line_start": <line start>, "line_end": <line end>}, ..., ].
                """.trimIndent()
    ),
    OpenAiChatMessage(
        "user",
        """
        280. public void connect(Figure figure) {
        281.     if (fObservedFigure != null)
        282.         fObservedFigure.removeFigureChangeListener(this);
        283. 
        284.     fObservedFigure = figure;
        285.     fLocator = new OffsetLocator(figure.connectedTextLocator(this));
        286.     fObservedFigure.addFigureChangeListener(this);
        287.     if (fLocator != null) {
        288.         Point p = fLocator.locate(fObservedFigure);
        289.         p.x -= size().width/2 + fOriginX;
        290.         p.y -= size().height/2 + fOriginY;
        291.     
        292.         if (p.x != 0 || p.y != 0) {
        293.             willChange();
        294.             basicMoveBy(p.x, p.y);
        295.             changed();
        296.         }
        297.     }
        298. }
    """.trimIndent()
    ),
    OpenAiChatMessage(
        "assistant",
        """
                [
                {"function_name":  "updateLocator", "line_start":  288, "line_end": 296}
                ]
                """.trimIndent()
    ),
    OpenAiChatMessage(
        "user",
        """
        92.  public void mouseUp(MouseEvent e, int x, int y) {
        93.      if (e.isPopupTrigger()) {
        94.          Figure figure = drawing().findFigure(e.getX(), e.getY());
        95.          if (figure != null) {
        96.              Object attribute = figure.getAttribute(Figure.POPUP_MENU);
        97.              if (attribute == null) {
        98.                  figure = drawing().findFigureInside(e.getX(), e.getY());
        99.              }
        100.             if (figure != null) {
        101.                 showPopupMenu(figure, e.getX(), e.getY(), e.getComponent());
        102.             }
        103.         }
        104.     }
        105.     else if (e.getClickCount() == 2) {
        106.         handleMouseDoubleClick(e, x, y);
        107.     }
        108.     else {
        109.         super.mouseUp(e, x, y);
        110.         handleMouseUp(e, x, y);
        111.         handleMouseClick(e, x, y);
        112.     }
        113. }
    """.trimIndent()
    ),
    OpenAiChatMessage(
        "assistant",
        """
                [
                {"function_name":  "computeFigure", "line_start":  94, "line_end": 103},
                {"function_name":  "computeAttribute", "line_start":  96, "line_end": 102}
                ]
                """.trimIndent()
    ),
    OpenAiChatMessage(
        "user",
        codeSnippet
    )
)