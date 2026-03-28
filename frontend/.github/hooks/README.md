# Git Hooks

Pour activer les hooks localement (une seule fois par machine) :

    git config core.hooksPath .github/hooks

Ces hooks vérifient :
- Qu'aucun snake_case n'est introduit dans les types TypeScript
- Que les champs booléens ne sont pas préfixés `is` (le backend retourne `active`, `profilePublic`, pas `isActive`, etc.)
- Que la documentation est mise à jour quand du code critique change

Pour bypasser (à utiliser avec parcimonie) :

    git commit --no-verify
