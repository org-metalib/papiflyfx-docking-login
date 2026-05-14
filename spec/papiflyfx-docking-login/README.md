# papiflyfx-docking-login

## Prompt ask for login component

As a JavaFX docking framework developer, I research to develop a login docking component that provides the follwoing features:
- Docking framework application login prompt
- Support for a multiple identity providers, like Google, Facebook, GitHub, Apple, Amaxon, etc.
- Session management
- Session secret management

Help me witht the concept of the login component and create spec/papiflyfx-docking-login/login-chatgpt.md

Here is the link to the docking framework repo: https://github.com/org-metalib/papiflyfx-docking


## Planning

- plan-copilot-opus.md
- plan-copilot-sonnet.md
- plan-codex.md

Write a very detailed `spec/papiflyfx-docking-login/plan-junie-opus.md` document outlining how to implement this component.
Include code snippets. Do not include compatibility requirements.

incorporate ideas from the following sources:
- spec/papiflyfx-docking-login/login-chatgpt.md
- spec/papiflyfx-docking-login/login-gemini.md
- spec/papiflyfx-docking-login/login-grok.md

## Plan Synthesis

Read three implementations of the github component:
- spec/papiflyfx-docking-login/plan-codex.md
- spec/papiflyfx-docking-login/plan-gemini.md
- spec/papiflyfx-docking-login/plan-copilot-sonnet.md

Merge the three implementations into a single plan: spec/papiflyfx-docking-login/plan.md
Identify the conflicts and ask for clarification if necessary.

## Plan refactoring

## Plan Extra 0
check spec/papiflyfx-docking-login/plan.md, with settings module in mind what else would be added. create spec/papiflyfx-docking-login/plan-extra0.md with the list     

## Plan Extra 1

For the login component, in general, there are two pluggable types: identity provider and session management.
Identity providers can be Google, Facebook, GitHub, Apple, Amazon, etc.
Session management involves handling user sessions,
session secrets, and session expiration. The login component should be designed to be flexible and extensible,
allowing for easy integration with different identity providers and session management systems.
Create spec/papiflyfx-docking-login/plan-extra1-pluggins.md with the list of the components that need to be implemented.
Most likely, we will need to have two sets of apis that could be wrapped as separate modules:
- login-idapi
- login-session-api

## Plan Composition

Merge the follwoing list of the specifications resolving any overlappings and conflicts into spec/papiflyfx-docking-login/plan1.md:
- spec/papiflyfx-docking-login/plan.md
- spec/papiflyfx-docking-login/plan-extra0.md
- spec/papiflyfx-docking-login/plan-extra1-pluggins.md

## Implementation

- Implement everything specified by `spec/papiflyfx-docking-login/plan1.md`. when you’re done with a task or phase, mark it
  as completed in the `spec/papiflyfx-docking-login/plan1.md` document.
- Add new `spec/papiflyfx-docking-login/progress.md` file to track your progress.
- Do not stop until all tasks and phases are completed. Do not add unnecessary comments or javadocs, do not use any or unknown types.
- Continuously run typecheck to make sure you’re not introducing new issues.
- add github module readme

