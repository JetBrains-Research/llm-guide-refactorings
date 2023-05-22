# LLMs guide IDE refactorings

<!-- Plugin description -->
Researching collaboration between LLMs and IDEs to perform automated refactorings.
<!-- Plugin description end -->

### Table of contents

- [Getting started](#getting-started)

## Getting started

- Sign up for OpenAI at https://beta.openai.com/signup
- Get your OpenAI API key
- Go to Settings | Tools | Large Language Models and enter your API key in the "OpenAI Key" field. If you are a member
  of only one organization, leave the "OpenAI Organization" field empty
- Select a code fragment, press Alt-Enter and select "apply custom edit" intention
- Write an instruction for the LLM and wait for the result

## Triggering Extract Function with ChatGPT
To trigger the Extract Function with ChatGPT simply right click inside a function, without selecting anything
then, select *Show Context Action -> Extract Function Experiment*. This action will automatically select the entire
function, including the doc string, and it will form a ChatGPT prompt. When ChatGPT's reply comes back,
the first suggestion will be selected, and, if there is any viable suggestion, automatically the block of
code will be selected and extracted in a new function.

Additionally, ChatGPT can be also triggered with a selection, however it is better to be triggered with an 
entire function, because this way it has more context and can provide better suggestions.